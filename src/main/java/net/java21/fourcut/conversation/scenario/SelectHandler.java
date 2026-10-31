package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import org.springframework.stereotype.Component;

/** SELECT: 후보를 점수순으로 상위 5개만 남기고 1등을 고른다. 후보가 없으면 NO_SCENE. GPU를 쓰지 않는다. */
@Component
class SelectHandler implements StepHandler {

  static final int KEEP = 5;

  private final ScenarioStore store;

  SelectHandler(ScenarioStore store) {
    this.store = store;
  }

  @Override
  public Step step() {
    return Step.SELECT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    UUID id = context.conversation().id();
    List<SceneCandidate> top =
        store.candidates(id).stream()
            .filter(SceneCandidate::valid)
            .sorted(Comparator.comparingDouble(SceneCandidate::score).reversed())
            .limit(KEEP)
            .toList();
    if (top.isEmpty()) {
      return new StepOutcome.NoScene(store.saveCandidates(id, List.of()));
    }
    List<SceneCandidate> marked = new ArrayList<>(top.size());
    marked.add(top.get(0).select());
    marked.addAll(top.subList(1, top.size()));
    return new StepOutcome.Done(store.saveCandidates(id, marked), Step.SCRIPT);
  }
}
