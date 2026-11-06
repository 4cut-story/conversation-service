package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.springframework.stereotype.Component;

/**
 * SELECT: 후보를 점수순으로 상위 5개만 남기고 1등을 고른다. 후보가 없으면 말이 가장 많이 오간 몇 분을 "있었던 일을 기록하는" 장면으로 쓴다. 특별한 순간이 없다고
 * 장면 없음으로 끝내면 사용자는 녹음하고도 만화를 받지 못했다. 발화가 하나도 없을 때만 NO_SCENE. GPU를 쓰지 않는다.
 */
@Component
class SelectHandler implements StepHandler {

  static final int KEEP = 5;

  /** 기록 장면 길이(초). 콘티 한 편에 담기 좋은 길이. */
  static final double DIARY_SEC = 180;

  static final String DIARY_SUMMARY = "특별한 사건은 없지만, 이 몇 분 동안 있었던 일을 일기처럼 차례대로 기록한다.";

  private final ScenarioStore store;
  private final TranscriptStore transcripts;

  SelectHandler(ScenarioStore store, TranscriptStore transcripts) {
    this.store = store;
    this.transcripts = transcripts;
  }

  @Override
  public Step step() {
    return Step.SELECT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    List<SceneCandidate> top =
        store.candidates(id).stream()
            .filter(SceneCandidate::valid)
            .sorted(Comparator.comparingDouble(SceneCandidate::score).reversed())
            .limit(KEEP)
            .toList();
    if (top.isEmpty()) {
      Optional<SceneCandidate> diary = diary(transcripts.load(id));
      if (diary.isEmpty()) {
        return new StepOutcome.NoScene(store.saveCandidates(id, List.of()));
      }
      top = List.of(diary.get());
    }
    List<SceneCandidate> marked = new ArrayList<>(top.size());
    marked.add(top.get(0).select());
    marked.addAll(top.subList(1, top.size()));
    return new StepOutcome.Done(store.saveCandidates(id, marked), Step.SCRIPT);
  }

  /** 말이 가장 많이 오간 {@link #DIARY_SEC}초를 기록 장면으로. 발화가 없으면 없다. */
  static Optional<SceneCandidate> diary(Transcript transcript) {
    List<Transcript.Utterance> u = transcript.utterances();
    if (u.isEmpty()) {
      return Optional.empty();
    }
    int bestFrom = 0;
    int bestTo = 0;
    int to = 0;
    for (int from = 0; from < u.size(); from++) {
      to = Math.max(to, from);
      while (to + 1 < u.size() && u.get(to + 1).start() <= u.get(from).start() + DIARY_SEC) {
        to++;
      }
      if (to - from > bestTo - bestFrom) {
        bestFrom = from;
        bestTo = to;
      }
    }
    double start = u.get(bestFrom).start();
    double end = Math.max(u.get(bestTo).end(), start + 1);
    return Optional.of(
        new SceneCandidate("CORE", start, end, DIARY_SUMMARY, "장면 후보가 없어 있었던 일을 기록한다", 0.0, null));
  }
}
