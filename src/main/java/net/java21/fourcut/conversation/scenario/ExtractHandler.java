package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * EXTRACT: 전사를 10분 구간으로 나눠 구간마다 LLM으로 장면 후보(0~2개)를 뽑는다. 구간을 하나씩 차례로 작업으로 넣는다. 외부 LLM
 * API(Claude·Gemini)를 쓸 수 있으면 먼저 서버에서 전사의 잘못 들은 말을 고치고({@link TranscriptCorrector}), 이후 단계(장면
 * 선택·콘티·대사)는 고친 전사를 쓴다. 교정이 실패하면 원문 그대로 간다.
 */
@Component
class ExtractHandler implements StepHandler {

  private static final Logger log = LoggerFactory.getLogger(ExtractHandler.class);

  private final TranscriptStore transcripts;
  private final ScenarioStore store;
  private final LlmJobs llm;
  private final ScenarioProperties properties;
  private final TranscriptCorrector corrector;

  ExtractHandler(
      TranscriptStore transcripts,
      ScenarioStore store,
      LlmJobs llm,
      ScenarioProperties properties,
      TranscriptCorrector corrector) {
    this.transcripts = transcripts;
    this.store = store;
    this.llm = llm;
    this.properties = properties;
    this.corrector = corrector;
  }

  @Override
  public Step step() {
    return Step.EXTRACT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    if (properties.correctTranscript() && corrector.available()) {
      // 외부 LLM 호출은 시간이 걸리므로 트랜잭션 밖(runLocal)에서 한다
      return new StepOutcome.RunLocal();
    }
    return firstWindow(context, transcripts.load(context.conversation().publicId()));
  }

  @Override
  public StepOutcome runLocal(StepContext context) {
    String id = context.conversation().publicId();
    Transcript transcript = transcripts.loadOriginal(id);
    try {
      Transcript corrected = corrector.correct(transcript);
      if (!corrected.equals(transcript)) {
        transcripts.saveCorrected(id, corrected);
        transcript = corrected;
      }
    } catch (RuntimeException e) {
      log.warn("전사 교정 실패, 원문으로 진행: conversation={}", id, e);
      transcript = transcripts.load(id);
    }
    return firstWindow(context, transcript);
  }

  private StepOutcome firstWindow(StepContext context, Transcript transcript) {
    String id = context.conversation().publicId();
    store.saveCandidates(id, List.of());
    store.saveNames(id, List.of());
    return nextWindow(context, transcript, -1);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    JsonNode content = llm.content(result);
    Transcript transcript = transcripts.load(id);
    int window = context.jobPayload().path("window").asInt();
    List<Transcript.Utterance> lines = TranscriptText.merge(part(transcript, window));
    List<SceneCandidate> found = new ArrayList<>(store.candidates(id));
    for (JsonNode node : content.path("candidates")) {
      SceneCandidate c = llm.read(SceneTimes.resolve(node, lines), SceneCandidate.class).scored();
      if (c.valid()) {
        found.add(c);
      }
    }
    store.saveCandidates(id, found);
    store.saveNames(
        id, mergeNames(store.names(id), content.path("names"), ScriptHandler.spoken(transcript)));
    return nextWindow(context, transcript, window);
  }

  /** 구간마다 찾은 실명을 합친다. 대화에 실제로 나오고 이름처럼 보이는 단어만 남긴다. */
  static List<String> mergeNames(List<String> known, JsonNode found, String spokenText) {
    Set<String> names = new LinkedHashSet<>(known);
    for (JsonNode n : found) {
      String word = n.asString("").strip();
      if (RealNames.plausible(word, spokenText)) {
        names.add(word);
      }
    }
    return List.copyOf(names);
  }

  private List<Transcript.Utterance> part(Transcript transcript, int window) {
    int size = properties.windowSec();
    return transcript.between((double) window * size, (double) (window + 1) * size);
  }

  private StepOutcome nextWindow(StepContext context, Transcript transcript, int after) {
    int size = properties.windowSec();
    int last = transcript.durationSec() / size;
    for (int w = after + 1; w <= last; w++) {
      List<Transcript.Utterance> part = part(transcript, w);
      if (!part.isEmpty()) {
        ObjectNode extra = llm.object();
        extra.put("window", w);
        String prompt =
            "대화 언어: "
                + transcript.language()
                + "\n화자: "
                + String.join(", ", transcript.speakers())
                + "\n\n전사 ("
                + (w * size / 60)
                + "분부터):\n"
                + TranscriptText.compact(part);
        long seed = (long) context.conversation().publicId().hashCode() + w + context.attempt();
        return llm.job(
            Prompts.EXTRACT_SYSTEM,
            prompt,
            Prompts.EXTRACT_SCHEMA,
            Prompts.EXTRACT_MAX_TOKENS,
            seed,
            extra);
      }
    }
    return new StepOutcome.Done(
        WorkKeys.candidates(context.conversation().publicId()), Step.SELECT);
  }
}
