package net.java21.fourcut.conversation.scenario;

import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import org.springframework.stereotype.Component;

/**
 * REVIEW: 콘티 규칙을 코드로 다시 확인하고 검수 표시를 남긴다. 맞춤법·순화·실명 바꾸기는 SCRIPT의 LLM 호출이 함께 한다 (LLM 호출 1회 절약). GPU를
 * 쓰지 않는다.
 */
@Component
class ReviewHandler implements StepHandler {

  private final ScenarioStore store;

  ReviewHandler(ScenarioStore store) {
    this.store = store;
  }

  @Override
  public Step step() {
    return Step.REVIEW;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    ComicScript script = store.script(id);
    String violation = script.violation();
    if (violation != null) {
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    return new StepOutcome.Done(
        store.saveScript(id, script.reviewedWith(script.nameWarnings())), Step.DRAW);
  }
}
