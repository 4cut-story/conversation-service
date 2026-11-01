package net.java21.fourcut.conversation.scenario;

import java.util.List;
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
    SceneCandidate scene =
        store.candidates(id).stream()
            .filter(SceneCandidate::chosen)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("고른 장면이 없다"));
    String prompt = prompt(transcripts.load(id), scene);
    long seed = (long) id.hashCode() + context.attempt();
    return llm.job(
        Prompts.SCRIPT_SYSTEM,
        prompt,
        Prompts.SCRIPT_SCHEMA,
        Prompts.SCRIPT_MAX_TOKENS,
        seed,
        null);
  }

  /** 콘티 LLM 입력: 고른 장면 정보와 앞뒤 1분 대화. */
  static String prompt(Transcript transcript, SceneCandidate scene) {
    List<Transcript.Utterance> part =
        transcript.between(scene.start() - CONTEXT_SEC, scene.end() + CONTEXT_SEC);
    return "대화 언어: "
        + transcript.language()
        + "\n화자: "
        + String.join(", ", transcript.speakers())
        + "\n장면 종류: "
        + scene.kind()
        + "\n장면 요약: "
        + scene.summary()
        + "\n고른 이유: "
        + scene.reason()
        + "\n\n장면 앞뒤 대화:\n"
        + TranscriptText.compact(part);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    ComicScript script = llm.read(llm.content(result), ComicScript.class).normalized();
    String violation = script.violation();
    if (violation != null) {
      log.warn("콘티 규칙 위반: {} 출력 앞부분: {}", violation, head(result));
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    String id = context.conversation().publicId();
    script = script.withFictionalNames(spoken(transcripts.load(id)));
    return new StepOutcome.Done(store.saveScript(id, script), Step.REVIEW);
  }

  private static String head(JsonNode result) {
    String content = result.path("content").asString("");
    return content.length() > 1500 ? content.substring(0, 1500) + "…" : content;
  }

  static String spoken(Transcript transcript) {
    StringBuilder sb = new StringBuilder();
    transcript.utterances().forEach(u -> sb.append(u.text()).append('\n'));
    return sb.toString();
  }
}
