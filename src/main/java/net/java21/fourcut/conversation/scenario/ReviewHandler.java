package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.List;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** REVIEW: 맞춤법·말풍선 길이를 다듬고 남은 실명을 가명으로 바꾼다. 바꾼 단어는 올리기 전 경고(nameWarnings)로 남긴다. */
@Component
class ReviewHandler implements StepHandler {

  private final ScenarioStore store;
  private final LlmJobs llm;

  ReviewHandler(ScenarioStore store, LlmJobs llm) {
    this.store = store;
    this.llm = llm;
  }

  @Override
  public Step step() {
    return Step.REVIEW;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    ComicScript script = store.script(id);
    String prompt = "검수할 콘티 (JSON):\n" + llm.write(script);
    long seed = (long) id.hashCode() ^ context.attempt();
    return llm.job(Prompts.REVIEW_SYSTEM, prompt, Prompts.REVIEW_SCHEMA, seed, null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    JsonNode content = llm.content(result);
    ComicScript script = llm.read(content.path("script"), ComicScript.class);
    String violation = script.violation();
    if (violation != null) {
      return new StepOutcome.Retry("검수 결과 규칙 위반: " + violation);
    }
    List<String> warnings = new ArrayList<>();
    content.path("nameWarnings").forEach(n -> warnings.add(n.asString()));
    String id = context.conversation().publicId();
    return new StepOutcome.Done(store.saveScript(id, script.reviewedWith(warnings)), Step.DRAW);
  }
}
