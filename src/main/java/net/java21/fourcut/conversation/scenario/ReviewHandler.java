package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.List;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * REVIEW: 콘티 규칙을 코드로 다시 확인하고 검수 표시를 남긴다. 맞춤법·순화·실명 바꾸기는 SCRIPT의 LLM 호출이 함께 한다.
 *
 * <p>말투가 있는 컨셉(조선시대·무협·동물·그림책)은 여기서 LLM에 대사만 따로 넘겨 그 말투로 바꾼다. 대사가 장면 안 대화인지는 SCRIPT에서 원래 대사로 이미
 * 확인했다. 말투 바꾸기가 실패해도 원래 대사로 끝까지 만든다. 일상 컨셉은 GPU를 쓰지 않는다.
 */
@Component
class ReviewHandler implements StepHandler {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ReviewHandler.class);

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
    String violation = script.violation();
    if (violation != null) {
      return new StepOutcome.Retry("콘티 규칙 위반: " + violation);
    }
    Concept concept = Concept.of(script.concept());
    if (concept.voice().isBlank() || script.lineTexts().isEmpty()) {
      return done(id, script);
    }
    long seed = (long) id.hashCode() + context.attempt();
    return llm.job(
        Prompts.voiceSystem(concept),
        prompt(script),
        Prompts.voiceSchema(script.lineTexts().size()),
        Prompts.VOICE_MAX_TOKENS,
        seed,
        null);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    ComicScript script = store.script(id);
    ComicScript voiced = script;
    try {
      JsonNode out = llm.content(result);
      List<String> lines = new ArrayList<>();
      out.path("lines").forEach(n -> lines.add(n.asString("")));
      List<String> rejected = script.voiceRejections(lines);
      if (!rejected.isEmpty()) {
        log.warn("말투 바꾼 대사 일부를 원래 대사로 둔다 ({}): {}", id, rejected);
      }
      voiced = script.withVoices(out.path("title").asString(""), lines);
    } catch (RuntimeException e) {
      log.warn("말투 바꾸기 결과를 읽지 못해 원래 대사를 쓴다: {}", e.getMessage());
    }
    if (voiced.violation() != null) {
      log.warn("말투 바꾼 콘티가 규칙에 어긋나 원래 대사를 쓴다: {}", voiced.violation());
      voiced = script;
    }
    return done(id, voiced);
  }

  /** 말투 바꾸기 입력: 제목과 번호 붙인 대사. */
  static String prompt(ComicScript script) {
    StringBuilder sb = new StringBuilder("제목: ").append(script.title()).append("\n대사:\n");
    List<String> lines = script.lineTexts();
    for (int i = 0; i < lines.size(); i++) {
      sb.append(i + 1).append(". ").append(lines.get(i)).append('\n');
    }
    return sb.toString();
  }

  private StepOutcome done(String id, ComicScript script) {
    return new StepOutcome.Done(
        store.saveScript(id, script.reviewedWith(script.nameWarnings())), Step.DRAW);
  }
}
