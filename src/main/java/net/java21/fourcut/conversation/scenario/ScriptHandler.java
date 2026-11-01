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

/** SCRIPT: 고른 장면과 앞뒤 1분 대화로 4컷 콘티를 만든다 (LLM이 3안을 만들어 스스로 채점해 1안만 낸다). */
@Component
class ScriptHandler implements StepHandler {

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
    Transcript transcript = transcripts.load(id);
    List<Transcript.Utterance> part =
        transcript.between(scene.start() - CONTEXT_SEC, scene.end() + CONTEXT_SEC);
    String prompt =
        "대화 언어: "
            + transcript.language()
            + "\n장면 종류: "
            + scene.kind()
            + "\n장면 요약: "
            + scene.summary()
            + "\n고른 이유: "
            + scene.reason()
            + "\n\n장면 앞뒤 대화:\n"
            + Transcript.lines(part);
    long seed = (long) id.hashCode() + context.attempt();
    return llm.job(Prompts.SCRIPT_SYSTEM, prompt, Prompts.SCRIPT_SCHEMA, seed, null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    ComicScript script = llm.read(llm.content(result), ComicScript.class);
    String violation = script.violation();
    if (violation != null) {
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    return new StepOutcome.Done(
        store.saveScript(context.conversation().publicId(), script), Step.REVIEW);
  }
}
