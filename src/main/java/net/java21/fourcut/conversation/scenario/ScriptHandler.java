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
    long seed = (long) id.hashCode() + context.attempt();
    return llm.job(
        Prompts.SCRIPT_SYSTEM,
        prompt,
        Prompts.SCRIPT_SCHEMA,
        Prompts.SCRIPT_MAX_TOKENS,
        seed,
        null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    ComicScript script = llm.read(llm.content(result), ComicScript.class);
    String violation = script.violation();
    if (violation != null) {
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    String id = context.conversation().publicId();
    List<String> clashes = script.namesFoundIn(spoken(transcripts.load(id)));
    if (!clashes.isEmpty()) {
      return new StepOutcome.Retry("가명이 대화에 나온 말과 겹친다: " + String.join(", ", clashes));
    }
    return new StepOutcome.Done(store.saveScript(id, script), Step.REVIEW);
  }

  private static String spoken(Transcript transcript) {
    StringBuilder sb = new StringBuilder();
    transcript.utterances().forEach(u -> sb.append(u.text()).append('\n'));
    return sb.toString();
  }
}
