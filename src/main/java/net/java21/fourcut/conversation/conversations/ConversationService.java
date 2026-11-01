package net.java21.fourcut.conversation.conversations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.pipeline.PipelineService;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.support.Cursor;
import net.java21.fourcut.conversation.support.CursorPage;
import net.java21.fourcut.conversation.support.PublicId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 대화 만들기, 녹음 조각 올리기, 처리 시작, 진행 상태, 삭제. 대화는 주인만 본다(다르면 404). */
@Service
public class ConversationService {

  /** 녹음 조각은 최대 60개 (1분 조각 기준 1시간). */
  public static final int MAX_CHUNKS = 60;

  /** 조각 올리기 주소 유효 시간. */
  public static final Duration CHUNK_URL_TTL = Duration.ofMinutes(5);

  private static final List<String> AUDIO_TYPES =
      List.of(
          "audio/webm",
          "audio/ogg",
          "audio/mp4",
          "audio/mpeg",
          "audio/wav",
          "audio/x-wav",
          "audio/x-m4a",
          "audio/aac");

  private final ConversationRepository conversations;
  private final PipelineService pipeline;
  private final ObjectStorage storage;
  private final JsonMapper json;
  private final Clock clock;

  public ConversationService(
      ConversationRepository conversations,
      PipelineService pipeline,
      ObjectStorage storage,
      JsonMapper json,
      Clock clock) {
    this.conversations = conversations;
    this.pipeline = pipeline;
    this.storage = storage;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public ConversationApi.Created create(String owner, ConversationApi.CreateRequest request) {
    if (!Boolean.TRUE.equals(request.consent())) {
      throw new ApiException(ErrorCode.CONSENT_REQUIRED);
    }
    Conversation saved =
        conversations.save(
            new Conversation(
                owner,
                request.source(),
                request.language(),
                request.chosenConcept(),
                clock.instant()));
    return new ConversationApi.Created(saved.getPublicId(), saved.getStatus());
  }

  /** 녹음 조각 하나를 올릴 사전 서명 PUT 주소. web 서버가 받아 조각을 대신 올린다. */
  @Transactional(readOnly = true)
  public ConversationApi.ChunkUpload chunk(
      String owner, String id, ConversationApi.ChunkRequest request) {
    Conversation c = owned(owner, id);
    requireRecording(c);
    if (request.index() >= MAX_CHUNKS) {
      throw new ApiException(ErrorCode.TOO_LONG);
    }
    String type = request.contentType().split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
    if (!AUDIO_TYPES.contains(type)) {
      throw new ApiException(ErrorCode.UNSUPPORTED_AUDIO);
    }
    String url = storage.presignPut(WorkKeys.audioChunk(id, request.index()), null, CHUNK_URL_TTL);
    return new ConversationApi.ChunkUpload(url, CHUNK_URL_TTL.toSeconds());
  }

  /** 녹음을 끝내고 처리를 시작한다. 조각이 모두 올라와 있어야 한다. */
  @Transactional
  public ConversationApi.Finished finish(String owner, String id, int chunkCount) {
    Conversation c = owned(owner, id);
    requireRecording(c);
    if (chunkCount > MAX_CHUNKS) {
      throw new ApiException(ErrorCode.TOO_LONG);
    }
    boolean missing =
        IntStream.range(0, chunkCount).anyMatch(i -> !storage.exists(WorkKeys.audioChunk(id, i)));
    if (missing) {
      throw new ApiException(ErrorCode.AUDIO_MISSING);
    }
    c.queue(chunkCount);
    conversations.flush();
    pipeline.start(c.getId());
    return new ConversationApi.Finished(c.getStatus());
  }

  @Transactional(readOnly = true)
  public ConversationApi.View get(String owner, String id) {
    return view(owned(owner, id));
  }

  @Transactional(readOnly = true)
  public CursorPage<ConversationApi.View> list(String owner, String cursor, Integer size) {
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return CursorPage.of(
        conversations.page(owner, from.at(), from.id(), Limit.of(limit + 1)),
        limit,
        c -> new Cursor(c.getCreatedAt(), c.getId()),
        page -> page.stream().map(this::view).toList());
  }

  /** 장면 후보 (참고용, FR-004a). 아직 없으면 빈 목록. */
  @Transactional(readOnly = true)
  public JsonNode candidates(String owner, String id) {
    owned(owner, id);
    return storage
        .get(WorkKeys.candidates(id))
        .map(bytes -> json.readTree(new String(bytes, StandardCharsets.UTF_8)))
        .filter(node -> node.has("candidates"))
        .orElseGet(() -> json.createObjectNode().set("candidates", json.createArrayNode()));
  }

  /** 대화와 중간 결과(음성, 단계 결과)를 지운다. 만화는 content-service 만화함에서 따로 지운다. */
  @Transactional
  public void delete(String owner, String id) {
    remove(owned(owner, id));
  }

  /** 탈퇴한 사용자의 대화를 모두 지운다 (멱등). */
  @Transactional
  public void deleteAllOf(String owner) {
    conversations.findByOwnerId(owner).forEach(this::remove);
  }

  private void remove(Conversation c) {
    storage.deletePrefix(WorkKeys.audioPrefix(c.getPublicId()));
    storage.deletePrefix(WorkKeys.workPrefix(c.getPublicId()));
    conversations.delete(c);
  }

  private ConversationApi.View view(Conversation c) {
    ConversationStatus stage = c.getStatus();
    ConversationStatus shown =
        stage.isProcessing() && pipeline.waitingForGpu(c.getId())
            ? ConversationStatus.GPU_WAITING
            : stage;
    return new ConversationApi.View(
        c.getPublicId(),
        c.getSource(),
        shown,
        stage,
        stage.progress(),
        pipeline.retries(c.getId()),
        c.getFailureReason(),
        c.getComicId(),
        c.getLanguage(),
        c.getConcept() == null ? "AUTO" : c.getConcept(),
        c.getDurationSec(),
        c.getCreatedAt());
  }

  /** 주인의 대화. 없거나, 남의 것이거나, 형식이 틀린 ID면 404. */
  private Conversation owned(String owner, String id) {
    if (!PublicId.isValid(id)) {
      throw ApiException.notFound();
    }
    return conversations
        .findByPublicId(id)
        .filter(c -> c.getOwnerId().equals(owner))
        .orElseThrow(ApiException::notFound);
  }

  private static void requireRecording(Conversation c) {
    if (c.getStatus() != ConversationStatus.RECORDING) {
      throw new ApiException(ErrorCode.CONFLICT, "이미 녹음을 마친 대화예요.");
    }
  }
}
