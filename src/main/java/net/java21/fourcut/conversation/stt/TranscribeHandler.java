package net.java21.fourcut.conversation.stt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * (FR-009).
 */
@Component
class TranscribeHandler implements StepHandler {

  private final TranscriptStore transcripts;
  private final ObjectStorage storage;
  private final PipelineObserver observer;
  private final JsonMapper json;

  TranscribeHandler(
      TranscriptStore transcripts,
      ObjectStorage storage,
      PipelineObserver observer,
      JsonMapper json) {
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
    UUID id = context.conversation().id();
    ObjectNode payload = json.createObjectNode();
    ArrayNode keys = payload.putArray("audioGetKeys");
    for (int i = 0; i < context.conversation().chunkCount(); i++) {
      keys.add(WorkKeys.audioChunk(id, i));
    }
    if (context.conversation().language() != null) {
      payload.put("language", context.conversation().language());
    }
    return new StepOutcome.Enqueue(JobType.TRANSCRIBE, payload, null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    JsonNode segments = result.path("segments");
    if (!segments.isArray()) {
      return new StepOutcome.Retry("전사 결과에 segments가 없다");
    }
    Transcript transcript = pseudonymize(segments, result.path("language").asString(null));
    UUID id = context.conversation().id();
    String key = transcripts.save(id, transcript);
    storage.deletePrefix(WorkKeys.audioPrefix(id));
    observer.transcribed(id, transcript.language(), transcript.durationSec());
    if (transcript.utterances().isEmpty()) {
      return new StepOutcome.NoScene(key);
    }
    return new StepOutcome.Done(key, Step.EXTRACT);
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
