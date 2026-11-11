package net.java21.fourcut.conversation.stt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.article.ArticleDialogue;
import net.java21.fourcut.conversation.article.ArticleStore;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * TRANSCRIBE: 녹음 조각을 GPU 워커(WhisperX + 화자 분리)에 넘기고, 결과 화자를 A, B, C… 가명으로 바꿔 저장한다. 끝나면 원본 음성을 지운다
 * (FR-009). 녹음 중 미리 전사한 앞부분({@link PreTranscriber})이 있으면 함께 넘긴다. 기사로 만든 대화는 녹음 대신 기사를 대화로 각색하는 LLM
 * 작업을 넣고, 그 대화를 전사로 저장한다.
 */
@Component
class TranscribeHandler implements StepHandler {

  private final TranscriptStore transcripts;
  private final ObjectStorage storage;
  private final PipelineObserver observer;
  private final JsonMapper json;
  private final ArticleStore articles;
  private final ArticleDialogue dialogue;

  TranscribeHandler(
      TranscriptStore transcripts,
      ObjectStorage storage,
      PipelineObserver observer,
      JsonMapper json,
      ArticleStore articles,
      ArticleDialogue dialogue) {
    this.articles = articles;
    this.dialogue = dialogue;
    this.transcripts = transcripts;
    this.storage = storage;
    this.observer = observer;
    this.json = json;
  }

  @Override
  public Step step() {
    return Step.TRANSCRIBE;
  }

  @Override
  public StepOutcome start(StepContext context) {
    if (context.conversation().article() != null) {
      // 기사로 만들기: 녹음 대신 기사를 대화로 각색하는 LLM 작업을 넣는다
      Article article = articles.load(context.conversation().publicId());
      return new StepOutcome.Enqueue(
          JobType.LLM, dialogue.payload(article), dialogue.modelInfo(article));
    }
    String id = context.conversation().publicId();
    ObjectNode payload = json.createObjectNode();
    ArrayNode keys = payload.putArray("audioGetKeys");
    for (int i = 0; i < context.conversation().chunkCount(); i++) {
      keys.add(WorkKeys.audioChunk(id, i));
    }
    if (context.conversation().language() != null) {
      payload.put("language", context.conversation().language());
    }
    // 녹음 중 미리 전사한 앞부분이 있으면 워커가 그 경계 뒤만 전사해 잇는다 (FR-015)
    if (storage.exists(WorkKeys.preTranscript(id))) {
      payload.put("prefixGetKey", WorkKeys.preTranscript(id));
    }
    return new StepOutcome.Enqueue(JobType.TRANSCRIBE, payload, null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    if (context.conversation().article() != null) {
      return onDialogue(context, result);
    }
    JsonNode segments = result.path("segments");
    if (!segments.isArray()) {
      return new StepOutcome.Retry("전사 결과에 segments가 없다");
    }
    Transcript transcript = pseudonymize(segments, result.path("language").asString(null));
    String id = context.conversation().publicId();
    String key = transcripts.save(id, transcript);
    storage.deletePrefix(WorkKeys.audioPrefix(id));
    observer.transcribed(
        context.conversation().id(), transcript.language(), transcript.durationSec());
    if (transcript.utterances().isEmpty()) {
      return new StepOutcome.NoScene(key);
    }
    return new StepOutcome.Done(key, Step.EXTRACT);
  }

  /** 기사를 각색한 대화를 전사로 저장한다. 결과가 규칙(대사 수, 베끼지 않기)을 어기면 다시 각색한다. */
  private StepOutcome onDialogue(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    ArticleDialogue.Dialogue parsed;
    try {
      parsed = dialogue.parse(result, articles.load(id));
    } catch (IllegalArgumentException e) {
      return new StepOutcome.Retry(e.getMessage());
    }
    Transcript transcript = fromDialogue(parsed);
    if (parsed.plan() != null) {
      articles.savePlan(id, parsed.plan());
    }
    String key = transcripts.save(id, transcript);
    observer.transcribed(context.conversation().id(), transcript.language(), 0);
    return new StepOutcome.Done(key, Step.EXTRACT);
  }

  /** 대사마다 4초씩 차례로 시각을 매긴다 (장면 구간을 나누는 데만 쓴다). */
  static Transcript fromDialogue(ArticleDialogue.Dialogue dialogue) {
    List<Transcript.Utterance> utterances = new ArrayList<>();
    List<String> speakers = new ArrayList<>();
    double at = 0;
    for (ArticleDialogue.Line line : dialogue.lines()) {
      if (!speakers.contains(line.speaker())) {
        speakers.add(line.speaker());
      }
      utterances.add(new Transcript.Utterance(line.speaker(), at, at + 3.5, line.text()));
      at += 4;
    }
    return new Transcript(dialogue.language(), List.copyOf(speakers), List.copyOf(utterances));
  }

  /** 워커가 준 화자 이름(SPEAKER_00 등)을 처음 나온 순서대로 A, B, C…로 바꾼다. */
  static Transcript pseudonymize(JsonNode segments, String language) {
    Map<String, String> names = new LinkedHashMap<>();
    List<Transcript.Utterance> utterances = new ArrayList<>();
    for (JsonNode s : segments) {
      String text = s.path("text").asString("").strip();
      if (text.isEmpty()) {
        continue;
      }
      String raw = s.path("speaker").asString("UNKNOWN");
      String speaker = names.computeIfAbsent(raw, k -> alias(names.size()));
      utterances.add(
          new Transcript.Utterance(
              speaker, s.path("start").asDouble(0), s.path("end").asDouble(0), text));
    }
    return new Transcript(language, List.copyOf(names.values()), utterances);
  }

  static String alias(int index) {
    return index < 26 ? String.valueOf((char) ('A' + index)) : "S" + (index + 1);
  }
}
