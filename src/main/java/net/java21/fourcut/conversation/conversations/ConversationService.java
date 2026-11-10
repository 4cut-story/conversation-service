package net.java21.fourcut.conversation.conversations;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.article.ArticleStore;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.pipeline.ManualLlmJobs;
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
  private final ArticleStore articles;
  private final ManualLlmJobs manualLlm;
  private final PipelineService pipeline;
  private final ObjectStorage storage;
  private final JsonMapper json;
  private final Clock clock;

  public ConversationService(
      ConversationRepository conversations,
      PipelineService pipeline,
      ManualLlmJobs manualLlm,
      ObjectStorage storage,
      JsonMapper json,
      Clock clock,
      ArticleStore articles) {
    this.conversations = conversations;
    this.articles = articles;
    this.pipeline = pipeline;
    this.manualLlm = manualLlm;
    this.storage = storage;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public ConversationApi.Created create(String owner, ConversationApi.CreateRequest request) {
    if (request.source() == Conversation.Source.ARTICLE) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "기사 주소를 넣어 주세요.");
    }
    if (!Boolean.TRUE.equals(request.consent())) {
      throw new ApiException(ErrorCode.CONSENT_REQUIRED);
    }
    Conversation c =
        new Conversation(
            owner, request.source(), request.language(), request.chosenConcept(), clock.instant());
    c.chooseDialect(request.chosenDialect());
    if (Boolean.TRUE.equals(request.manualLlm())) {
      c.markManualLlm();
    }
    Conversation saved = conversations.save(c);
    return new ConversationApi.Created(saved.getPublicId(), saved.getStatus());
  }

  /** 기사로 만들기. 이미 읽은 기사(본문)를 작업 폴더에 두고 바로 처리를 시작한다. 기사는 녹음 동의가 필요 없다. 전사 단계가 녹음 대신 기사를 대화로 각색한다. */
  @Transactional
  public ConversationApi.Created createFromArticle(
      String owner, ConversationApi.CreateRequest request, Article article) {
    Conversation c =
        new Conversation(
            owner,
            Conversation.Source.ARTICLE,
            request.language() == null ? article.language() : request.language(),
            request.chosenConcept(),
            clock.instant());
    c.chooseDialect(request.chosenDialect());
    if (Boolean.TRUE.equals(request.manualLlm())) {
      c.markManualLlm();
    }
    Conversation saved = startArticle(c, article);
    return new ConversationApi.Created(saved.getPublicId(), saved.getStatus());
  }

  /**
   * RSS 구독으로 새 기사를 만화로 만든다 (004 FR-059). 하루 만화 수 제한에 세지 않는다(FR-061). 바로 공개면 완성될 때 전체 공개로 게시한다.
   *
   * @param concept 그림체. null이면 자동
   * @param dialect 사투리. null이면 없음
   * @return 시작한 대화 공개 ID
   */
  @Transactional
  public String createFromFeed(
      String owner,
      long feedId,
      boolean autoPublish,
      String concept,
      String dialect,
      Article article) {
    Conversation c =
        new Conversation(
            owner, Conversation.Source.ARTICLE, article.language(), concept, clock.instant());
    c.chooseDialect(dialect);
    c.fromFeed(feedId, autoPublish);
    return startArticle(c, article).getPublicId();
  }

  private Conversation startArticle(Conversation c, Article article) {
    c.fromArticle(article.url(), article.title(), article.site());
    Conversation saved = conversations.save(c);
    articles.save(saved.getPublicId(), article);
    saved.queue(0);
    conversations.flush();
    pipeline.start(saved.getId());
    return saved;
  }

  /**
   * 녹음 조각 하나를 올릴 사전 서명 PUT 주소. web 서버가 받아 조각을 대신 올린다. 조각은 순서대로 하나씩 올라오므로, index번 조각을 물으면 그 앞 조각은 다
   * 올라온 것이다. 녹음이면 그 조각들로 구간 전사를 예약한다 (FR-015).
   */
  @Transactional
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
    int uploaded = request.index();
    if (c.getSource() == Conversation.Source.RECORDING
        && uploaded > 0
        && storage.exists(WorkKeys.audioChunk(id, uploaded - 1))) {
      pipeline.chunkUploaded(c.getId(), uploaded);
    }
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

  /** 테스트 전용: 답을 기다리는 수동 LLM 작업. 수동 처리 대화가 아니면 빈 목록. */
  @Transactional(readOnly = true)
  public List<ConversationApi.LlmJob> llmJobs(String owner, String id) {
    Conversation c = owned(owner, id);
    if (!c.isManualLlm()) {
      return List.of();
    }
    return manualLlm.waiting(c.getId()).stream()
        .map(j -> new ConversationApi.LlmJob(j.jobId(), j.step(), withImages(j.payload())))
        .toList();
  }

  /**
   * 그림이 든 작업(말풍선 자리 확인)은 저장소 키를 PNG base64(images)로 바꿔 준다. 순서는 질문 글 앞에 붙는 순서다. 저장소 주소는 클러스터 밖에서 열 수
   * 없어 바이트를 싣는다.
   */
  private JsonNode withImages(String payload) {
    tools.jackson.databind.node.ObjectNode node =
        (tools.jackson.databind.node.ObjectNode) json.readTree(payload);
    if (node.path("imageKeys").isArray()) {
      var images = node.putArray("images");
      node.path("imageKeys")
          .forEach(
              k ->
                  storage
                      .get(k.asString())
                      .ifPresent(b -> images.add(java.util.Base64.getEncoder().encodeToString(b))));
      node.remove("imageKeys");
    }
    return node;
  }

  /** 테스트 전용: 수동 LLM 작업에 답을 넣는다. 기다리는 작업이 아니면 404. */
  @Transactional
  public void answerLlmJob(String owner, String id, Long jobId, String content) {
    Conversation c = owned(owner, id);
    if (!c.isManualLlm() || !manualLlm.answer(c.getId(), jobId, content)) {
      throw ApiException.notFound();
    }
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
        c.getDialect() == null ? "NONE" : c.getDialect(),
        c.getDurationSec(),
        c.getCreatedAt(),
        c.getSourceUrl(),
        c.getSourceTitle());
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
