package net.java21.fourcut.conversation.scenario;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.java21.fourcut.conversation.article.ArticleStore;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * SCRIPT: 고른 장면과 앞뒤 1분 대화로 4~10컷 콘티(가명·호칭 인물, 컷별 구도, 대사)를 LLM 한 번에 만든다. 대사는 원문(quote)과 컨셉 말투로 다듬은
 * 말(text)을 함께 받아, 원문으로 장면 대조·빈 컷 채우기를 하고 말풍선에는 다듬은 말을 쓴다.
 */
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
  private final ArticleStore articles;

  ScriptHandler(
      TranscriptStore transcripts, ScenarioStore store, LlmJobs llm, ArticleStore articles) {
    this.transcripts = transcripts;
    this.store = store;
    this.llm = llm;
    this.articles = articles;
  }

  @Override
  public Step step() {
    return Step.SCRIPT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    SceneCandidate scene = chosen(id);
    Transcript transcript = transcripts.load(id);
    String prompt =
        prompt(
            transcript,
            scene,
            transcript.seed(),
            RealNames.whose(store.names(id), transcript),
            store.entities(id),
            articleBrief(context));
    long seed = transcript.seed() + context.attempt();
    return llm.job(
        Prompts.scriptSystem(concept(context, scene), Dialect.of(context.conversation().dialect())),
        prompt,
        Prompts.SCRIPT_SCHEMA,
        Prompts.SCRIPT_MAX_TOKENS,
        seed,
        null);
  }

  static String prompt(
      Transcript transcript, SceneCandidate scene, long castSeed, Map<String, String> names) {
    return prompt(transcript, scene, castSeed, names, Map.of(), null);
  }

  static String prompt(
      Transcript transcript,
      SceneCandidate scene,
      long castSeed,
      Map<String, String> names,
      Map<String, String> entities) {
    return prompt(transcript, scene, castSeed, names, entities, null);
  }

  /** 기사로 만든 대화의 각색 기획(흐름, 핵심 사실). 녹음이거나 기획이 없으면 null. */
  private String articleBrief(StepContext context) {
    if (context.conversation().article() == null) {
      return null;
    }
    return articles
        .plan(context.conversation().publicId())
        .map(net.java21.fourcut.conversation.article.ArticleDialogue.Plan::brief)
        .orElse(null);
  }

  /**
   * 콘티 LLM 입력: 화자별 가명, 고른 장면 정보, 장면 대화와 앞뒤 1분 대화. 대화 속 실명은 미리 가명으로, 실존 기업·인물 이름은 일반 표현으로 바꿔 LLM이 보지
   * 않게 한다.
   */
  static String prompt(
      Transcript transcript,
      SceneCandidate scene,
      long castSeed,
      Map<String, String> names,
      Map<String, String> entities,
      String articleBrief) {
    Map<String, String> cast = cast(transcript, castSeed);
    UnaryOperator<String> rename = scrub(RealNames.replacements(names, cast), entities);
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
        + "\n만화 분위기: "
        + Prompts.tone(scene.kind())
        + flow(scene, rename)
        + (articleBrief == null ? "" : "\n" + rename.apply(articleBrief))
        + "\n장면 요약: "
        + rename.apply(scene.summary())
        + "\n고른 이유: "
        + rename.apply(scene.reason())
        + peak(scene, rename)
        + "\n\n[장면 앞 대화 (참고만)]\n"
        + before
        + "\n\n[장면 대화 (대사는 여기서 고른다)]\n"
        + during
        + "\n\n[장면 뒤 대화 (참고만)]\n"
        + after;
  }

  /** 장면 추출이 정한 이 장면의 흐름(없으면 빈 문자열). */
  private static String flow(SceneCandidate scene, UnaryOperator<String> rename) {
    String flow = scene.flow();
    return flow == null || flow.isBlank() ? "" : "\n이 장면의 흐름: " + rename.apply(flow.strip());
  }

  /** 장면 추출이 찾은 핵심 순간(없으면 빈 문자열). 전 컷의 기준이 된다. */
  private static String peak(SceneCandidate scene, UnaryOperator<String> rename) {
    String peak = scene.peak();
    return peak == null || peak.isBlank() ? "" : "\n핵심 순간: " + rename.apply(peak.strip());
  }

  /** 발화 시작 시각으로 나눈 구간(겹치지 않는다). */
  private static String section(
      Transcript transcript, Predicate<Transcript.Utterance> in, UnaryOperator<String> rename) {
    List<Transcript.Utterance> part = transcript.utterances().stream().filter(in).toList();
    return part.isEmpty() ? "(없음)" : rename.apply(TranscriptText.compact(part));
  }

  /** 실명은 가명으로, 실존 기업·인물 이름은 일반 표현으로 바꾸는 함수. */
  static UnaryOperator<String> scrub(Map<String, String> rename, Map<String, String> entities) {
    return t -> Entities.apply(RealNames.apply(t, rename), entities);
  }

  /**
   * 등장인물에 없는 화자의 말을 다른 인물 대사로 쓴 콘티를 다시 쓰게 하는 횟수. 그 말이 반전·결말인 경우가 많아(funny-13 "똑똑, 옆집인데요") 먼저 다시 쓰게
   * 하고, 그래도 안 되면 그 대사를 빼고 넘어간다.
   */
  static final int UNCAST_RETRIES = 2;

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    Transcript transcript = transcripts.load(id);
    Map<String, String> cast = cast(transcript, transcript.seed());
    Map<String, String> rename =
        RealNames.replacements(RealNames.whose(store.names(id), transcript), cast);
    Map<String, String> entities = store.entities(id);
    UnaryOperator<String> clean = scrub(rename, entities);
    List<String> source = SpokenLines.sentences(clean.apply(spoken(transcript)));
    SceneCandidate scene = chosen(id);
    Concept concept = concept(context, scene);
    List<String> inScene = SpokenLines.sentences(clean.apply(sceneText(transcript, scene)));
    ComicScript script =
        llm.read(ScriptDraft.flatten(llm.content(result)), ComicScript.class)
            .normalized()
            .withNames(cast)
            .withFictionalNames(spoken(transcript))
            .replaced(rename)
            .generalized(t -> Entities.apply(t, entities))
            .withLineTexts(t -> SpokenLines.snap(t, source))
            .withLinesWhere(t -> SpokenLines.inside(t, inScene));
    List<ComicScript.Line> sceneLines = sceneLines(transcript, scene, clean);
    List<String> uncast = script.uncastSourceSpeakers(sceneLines);
    if (!uncast.isEmpty() && context.attempt() <= UNCAST_RETRIES) {
      String reason = "등장인물에 없는 화자 " + String.join(", ", uncast) + "의 말을 다른 인물 대사로 썼다";
      log.warn("콘티 규칙 위반: {}", reason);
      return new StepOutcome.Retry("콘티 규칙 위반: " + reason);
    }
    script =
        script
            .withSourceSpeakers(sceneLines)
            .withoutRepeatedLines()
            .withEmptyPanelsFilled(sceneLines(transcript, scene, clean))
            .withStyledLines(SpokenLines.MAX_CHARS)
            .withoutRepeatedLines()
            .scrubbed(w -> RealNames.called(w, spoken(transcript)))
            .withRoleCalls()
            .withCleanSummary()
            .withCleanMood()
            .withConcept(concept)
            .withMotionLimits()
            .withAmbientLimits();
    if (concept == Concept.ANIMALS) {
      script = script.withAnimals(animals(transcript.seed()));
    }
    // 생성 후 검사: 다듬기·빈 컷 채우기를 거친 뒤에도 실존 기업·인물 이름이 남았으면 한 번 더 일반 표현으로 바꾼다
    List<String> left = script.realEntities();
    if (!left.isEmpty()) {
      log.warn("콘티에 실존 기업·인물 이름이 남아 일반 표현으로 바꿨다: {}", left);
      script = script.generalized(t -> Entities.apply(t, entities));
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
    sceneUtterances(transcript, scene).forEach(u -> sb.append(u.text()).append('\n'));
    return sb.toString();
  }

  /** 장면 구간 발화를 시간 순서대로 말풍선 후보로 바꾼다(실명은 가명으로). 대사가 빈 컷을 채울 때 쓴다. */
  static List<ComicScript.Line> sceneLines(
      Transcript transcript, SceneCandidate scene, Map<String, String> rename) {
    return sceneLines(transcript, scene, t -> RealNames.apply(t, rename));
  }

  static List<ComicScript.Line> sceneLines(
      Transcript transcript, SceneCandidate scene, UnaryOperator<String> rename) {
    return sceneUtterances(transcript, scene).stream()
        .map(
            u ->
                new ComicScript.Line(
                    u.speaker(), SpokenLines.snap(rename.apply(u.text()), List.of())))
        .toList();
  }

  private static List<Transcript.Utterance> sceneUtterances(
      Transcript transcript, SceneCandidate scene) {
    return transcript.utterances().stream()
        .filter(
            u ->
                u.start() >= scene.start() - SCENE_SLACK_SEC
                    && u.start() <= scene.end() + SCENE_SLACK_SEC)
        .toList();
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
