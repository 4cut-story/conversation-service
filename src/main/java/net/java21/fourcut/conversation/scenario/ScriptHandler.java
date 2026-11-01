package net.java21.fourcut.conversation.scenario;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** SCRIPT: 고른 장면과 앞뒤 1분 대화로 4컷 콘티(가명 인물, 컷별 구도, 다듬은 대사)를 LLM 한 번에 만든다. */
@Component
class ScriptHandler implements StepHandler {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ScriptHandler.class);

  private static final double CONTEXT_SEC = 60;

  /** 대사로 인정하는 장면 구간 앞뒤 여유(초). 발화 시작 시각이 구간 경계에 걸친 말을 살린다. */
  static final double SCENE_SLACK_SEC = 5;

  private final TranscriptStore transcripts;
  private final ScenarioStore store;
  private final LlmJobs llm;

  ScriptHandler(TranscriptStore transcripts, ScenarioStore store, LlmJobs llm) {
    this.transcripts = transcripts;
    this.store = store;
    this.llm = llm;
  }

  @Override
  public Step step() {
    return Step.SCRIPT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    SceneCandidate scene = chosen(id);
    String prompt =
        prompt(
            transcripts.load(id),
            scene,
            id.hashCode(),
            RealNames.whose(store.names(id), transcripts.load(id)));
    long seed = (long) id.hashCode() + context.attempt();
    return llm.job(
        Prompts.scriptSystem(concept(context, scene)),
        prompt,
        Prompts.SCRIPT_SCHEMA,
        Prompts.SCRIPT_MAX_TOKENS,
        seed,
        null);
  }

  /** 콘티 LLM 입력: 화자별 가명, 고른 장면 정보, 장면 대화와 앞뒤 1분 대화. 대화 속 실명은 미리 가명으로 바꿔 LLM이 실명을 보지 않게 한다. */
  static String prompt(
      Transcript transcript, SceneCandidate scene, long castSeed, Map<String, String> names) {
    Map<String, String> cast = cast(transcript, castSeed);
    Map<String, String> rename = RealNames.replacements(names, cast);
    double from = scene.start();
    double to = scene.end();
    String before =
        section(transcript, u -> u.start() < from && u.end() >= from - CONTEXT_SEC, rename);
    String during = section(transcript, u -> u.start() >= from && u.start() <= to, rename);
    String after =
        section(transcript, u -> u.start() > to && u.start() <= to + CONTEXT_SEC, rename);
    return "대화 언어: "
        + transcript.language()
        + "\n화자: "
        + String.join(", ", transcript.speakers())
        + "\n인물 가명: "
        + CastNames.line(cast)
        + "\n장면 종류: "
        + scene.kind()
        + "\n장면 요약: "
        + RealNames.apply(scene.summary(), rename)
        + "\n고른 이유: "
        + RealNames.apply(scene.reason(), rename)
        + "\n\n[장면 앞 대화 (참고만)]\n"
        + before
        + "\n\n[장면 대화 (대사는 여기서 고른다)]\n"
        + during
        + "\n\n[장면 뒤 대화 (참고만)]\n"
        + after;
  }

  /** 발화 시작 시각으로 나눈 구간(겹치지 않는다). */
  private static String section(
      Transcript transcript, Predicate<Transcript.Utterance> in, Map<String, String> rename) {
    List<Transcript.Utterance> part = transcript.utterances().stream().filter(in).toList();
    return part.isEmpty() ? "(없음)" : RealNames.apply(TranscriptText.compact(part), rename);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    Transcript transcript = transcripts.load(id);
    Map<String, String> cast = cast(transcript, id.hashCode());
    Map<String, String> rename =
        RealNames.replacements(RealNames.whose(store.names(id), transcript), cast);
    List<String> source = SpokenLines.sentences(RealNames.apply(spoken(transcript), rename));
    SceneCandidate scene = chosen(id);
    Concept concept = concept(context, scene);
    List<String> inScene =
        SpokenLines.sentences(RealNames.apply(sceneText(transcript, scene), rename));
    ComicScript script =
        llm.read(llm.content(result), ComicScript.class)
            .normalized()
            .withNames(cast)
            .withFictionalNames(spoken(transcript))
            .replaced(rename)
            .scrubbed(w -> RealNames.called(w, spoken(transcript)))
            .withLineTexts(t -> SpokenLines.snap(t, source))
            .withLinesWhere(t -> SpokenLines.inside(t, inScene))
            .withConcept(concept);
    if (concept == Concept.ANIMALS) {
      script = script.withAnimals(animals(id.hashCode()));
    }
    String violation = script.violation();
    if (violation != null) {
      log.warn("콘티 규칙 위반: {} 출력 앞부분: {}", violation, head(result));
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    return new StepOutcome.Done(store.saveScript(id, script), Step.REVIEW);
  }

  /** 장면 구간(앞뒤 여유 SCENE_SLACK_SEC)의 대화. 대사는 이 안에서만 고른다. */
  static String sceneText(Transcript transcript, SceneCandidate scene) {
    StringBuilder sb = new StringBuilder();
    transcript.utterances().stream()
        .filter(
            u ->
                u.start() >= scene.start() - SCENE_SLACK_SEC
                    && u.start() <= scene.end() + SCENE_SLACK_SEC)
        .forEach(u -> sb.append(u.text()).append('\n'));
    return sb.toString();
  }

  /** 인물마다 다른 동물. 대화마다 시작 동물을 바꾼다. */
  static List<String> animals(long seed) {
    List<String> pool = Concept.ANIMAL_POOL;
    int start = (int) Math.floorMod(seed, (long) pool.size());
    List<String> out = new java.util.ArrayList<>();
    for (int i = 0; i < pool.size(); i++) {
      out.add(pool.get((start + i) % pool.size()));
    }
    return out;
  }

  private SceneCandidate chosen(String id) {
    return store.candidates(id).stream()
        .filter(SceneCandidate::chosen)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("고른 장면이 없다"));
  }

  /** 사용자가 고른 컨셉, 없으면(자동) 장면 추출이 장면에 맞게 고른 컨셉, 그것도 없으면 일상 웹툰. */
  static Concept concept(StepContext context, SceneCandidate scene) {
    return Concept.chosen(context.conversation().concept(), scene.concept());
  }

  private static String head(JsonNode result) {
    String content = result.path("content").asString("");
    return content.length() > 1500 ? content.substring(0, 1500) + "…" : content;
  }

  static Map<String, String> cast(Transcript transcript, long castSeed) {
    return CastNames.assign(transcript.speakers(), spoken(transcript), castSeed);
  }

  static String spoken(Transcript transcript) {
    StringBuilder sb = new StringBuilder();
    transcript.utterances().forEach(u -> sb.append(u.text()).append('\n'));
    return sb.toString();
  }
}
