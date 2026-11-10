package net.java21.fourcut.conversation.comic;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.voice.VoiceLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * SPEAK: 라이브 모드 대사 음성. 인물마다 겹치지 않게 목소리를 정하고(콘티 인물 설명·성별·나이대·사투리 → {@link VoiceLibrary#match}), 대사마다
 * 감정을 정해({@link SpeechCast}) 한 만화의 대사를 한 GPU 작업(SPEECH)으로 읽힌다. 결과(대사 키 → 음성 길이)는 {@link
 * WorkKeys#speech}에 두고 합성이 라이브 재생 정보에 넣는다. 기능 스위치가 꺼져 있거나 읽을 대사가 없으면 바로 합성으로 넘어간다. 실패하면 다시 하지 않고 소리
 * 없이 넘어간다(PipelineService).
 */
@Component
class SpeakHandler implements StepHandler {

  private static final Logger log = LoggerFactory.getLogger(SpeakHandler.class);

  /** 대사 읽기 빠르기 (1.0 = 참조 음성 그대로). */
  static final double SPEED = 1.0;

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final VoiceLibrary voices;
  private final JsonMapper json;
  private final boolean enabled;

  SpeakHandler(
      ScenarioStore scenarios,
      ObjectStorage storage,
      VoiceLibrary voices,
      JsonMapper json,
      @Value("${comic.speech.enabled:false}") boolean enabled) {
    this.scenarios = scenarios;
    this.storage = storage;
    this.voices = voices;
    this.json = json;
    this.enabled = enabled;
  }

  @Override
  public Step step() {
    return Step.SPEAK;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    // 목소리는 한국어 참조 음성이라 영어 만화는 읽지 않는다
    if (!enabled || "en".equals(context.conversation().language())) {
      return skip(id);
    }
    ComicScript script = scenarios.script(id);
    List<SpeechCast.Spoken> lines = SpeechCast.spoken(script);
    Map<String, String> cast = cast(script, context.conversation().dialect(), id);
    ObjectNode payload = json.createObjectNode();
    payload.put("conversationId", id);
    payload.put("speed", SPEED);
    ArrayNode out = payload.putArray("lines");
    for (SpeechCast.Spoken l : lines) {
      String voice = cast.get(l.speaker());
      if (voice == null) {
        continue;
      }
      ObjectNode line = out.addObject();
      line.put("key", l.key());
      line.put("voiceId", voice);
      line.put("emotion", l.emotion());
      line.put("text", l.text());
      line.put("uploadPutKey", WorkKeys.speechAudio(id, l.key()));
    }
    if (out.isEmpty()) {
      return skip(id);
    }
    ObjectNode info = json.createObjectNode();
    info.set("voices", json.valueToTree(cast));
    return new StepOutcome.Enqueue(JobType.SPEECH, payload, json.writeValueAsString(info));
  }

  /** 대사 음성 없이 합성으로 넘어간다. 예전 시도의 결과가 남아 있으면 지운다. */
  private StepOutcome skip(String id) {
    storage.put(WorkKeys.speech(id), "{\"lines\":{}}".getBytes(StandardCharsets.UTF_8), JSON);
    return new StepOutcome.Done(null, Step.COMPOSE);
  }

  private static final String JSON = "application/json";

  /** 화자 → 목소리 ID. 앞 인물에 준 목소리는 다른 후보가 있으면 피한다. 못 찾은 인물은 빠진다. */
  Map<String, String> cast(ComicScript script, String dialect) {
    return cast(script, dialect, "");
  }

  /** 점수가 같은 목소리가 여럿이면 만화(conversationId)와 인물마다 정해진 값으로 그중 하나를 고른다. */
  Map<String, String> cast(ComicScript script, String dialect, String conversationId) {
    Map<String, String> out = new LinkedHashMap<>();
    if (script.characters() == null) {
      return out;
    }
    Set<String> taken = new HashSet<>();
    String d = dialect == null || "NONE".equals(dialect) ? null : dialect;
    for (ComicScript.Character c : script.characters()) {
      String description =
          String.join(
              ", ",
              java.util.stream.Stream.of(c.role(), c.persona(), c.look())
                  .filter(s -> s != null && !s.isBlank())
                  .toList());
      Optional<VoiceLibrary.Voice> v =
          voices.match(
              new VoiceLibrary.Query(
                  description, SpeechCast.gender(c), SpeechCast.ageGroup(c), d, script.concept()),
              taken,
              (conversationId + ":" + c.speaker()).hashCode());
      v.ifPresent(
          x -> {
            out.put(c.speaker(), x.id());
            taken.add(x.id());
            // 벡터로 골랐는지(distance) 등록 순으로 골랐는지(null) 확인하려고 남긴다. 인물 설명은 남기지 않는다
            log.info(
                "목소리 배정: speaker={} gender={} age={} voice={} distance={}",
                c.speaker(),
                SpeechCast.gender(c),
                SpeechCast.ageGroup(c),
                x.id(),
                x.distance() == null
                    ? "없음"
                    : String.format(java.util.Locale.ROOT, "%.3f", x.distance()));
          });
    }
    return out;
  }

  /** 워커가 읽은 대사의 길이를 남긴다. 못 읽은 줄은 빠지고, 하나도 못 읽었어도 만화는 소리 없이 이어진다. */
  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    Set<String> asked = new HashSet<>();
    if (context.jobPayload() != null) {
      context.jobPayload().path("lines").forEach(l -> asked.add(l.path("key").asString("")));
    }
    ObjectNode saved = json.createObjectNode();
    ObjectNode lines = saved.putObject("lines");
    for (JsonNode l : result.path("lines")) {
      String key = l.path("key").asString("");
      int ms = l.path("durationMs").asInt(0);
      if (asked.contains(key) && ms > 0 && l.path("error").isMissingNode()) {
        lines.put(key, ms);
      }
    }
    log.info("대사 읽기: conversation={} {}/{}줄", id, lines.size(), asked.size());
    storage.put(
        WorkKeys.speech(id), json.writeValueAsString(saved).getBytes(StandardCharsets.UTF_8), JSON);
    return new StepOutcome.Done(WorkKeys.speech(id), Step.COMPOSE);
  }
}
