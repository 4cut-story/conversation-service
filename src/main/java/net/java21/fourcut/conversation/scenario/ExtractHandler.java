package net.java21.fourcut.conversation.scenario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * EXTRACT: 전사를 10분 구간으로 나눠 구간마다 LLM으로 장면 후보(0~2개)를 뽑는다. 외부 LLM API(Claude·Gemini)를 쓸 수 있으면 먼저 서버에서
 * 전사의 잘못 들은 말을 고치고({@link TranscriptCorrector}), 이후 단계(장면 선택·콘티·대사)는 고친 전사를 쓴다. 교정이 실패하면 원문 그대로 간다.
 *
 * <p>외부 API를 쓸 수 있으면 구간들을 대기열 없이 동시에 부른다 (구간끼리는 서로 기대지 않는다). 하나라도 실패하면 예전처럼 구간을 하나씩 차례로 작업으로 넣어 GPU
 * 워커(qwen)까지 넘어갈 수 있게 한다.
 */
@Component
class ExtractHandler implements StepHandler {

  private static final Logger log = LoggerFactory.getLogger(ExtractHandler.class);

  /** 구간을 동시에 부르는 수. 외부 API 동시 호출 한도를 넘지 않게 작게 둔다. */
  static final int CONCURRENCY = 4;

  /** 구간 동시 호출의 시간 한도. 교정(3분)과 합쳐 서버 단계 시간 초과(pipeline.local-step-timeout) 안에 끝나게 한다. */
  static final Duration DIRECT_TIME = Duration.ofMinutes(3);

  /** 구간이 하나뿐인 짧은 대화에서 후보가 없을 때 다시 물으며 붙이는 말. */
  static final String SHORT_HINT =
      "\n\n이 대화는 사용자가 만화로 만들려고 따로 녹음한 짧은 대화다. 빈 목록 대신 이 안에서 가장 나은 장면 하나를 고른다.";

  private final TranscriptStore transcripts;
  private final ScenarioStore store;
  private final LlmJobs llm;
  private final ScenarioProperties properties;
  private final TranscriptCorrector corrector;
  private final DirectLlm direct;

  /** {@code direct}는 지연 주입한다. 외부 LLM 실행기가 파이프라인 엔진을 거쳐 이 클래스에 다시 닿는 순환을 끊는다. */
  ExtractHandler(
      TranscriptStore transcripts,
      ScenarioStore store,
      LlmJobs llm,
      ScenarioProperties properties,
      TranscriptCorrector corrector,
      @Lazy DirectLlm direct) {
    this.transcripts = transcripts;
    this.store = store;
    this.llm = llm;
    this.properties = properties;
    this.corrector = corrector;
    this.direct = direct;
  }

  @Override
  public Step step() {
    return Step.EXTRACT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    if ((properties.correctTranscript() || properties.parallelExtract()) && direct.available()) {
      // 외부 LLM 호출은 시간이 걸리므로 트랜잭션 밖(runLocal)에서 한다
      return new StepOutcome.RunLocal();
    }
    return firstWindow(context, transcripts.load(context.conversation().publicId()));
  }

  @Override
  public StepOutcome runLocal(StepContext context) {
    String id = context.conversation().publicId();
    Transcript transcript = properties.correctTranscript() ? corrected(id) : transcripts.load(id);
    if (properties.parallelExtract()) {
      StepOutcome done = allWindows(context, transcript);
      if (done != null) {
        return done;
      }
    }
    return firstWindow(context, transcript);
  }

  private Transcript corrected(String id) {
    Transcript transcript = transcripts.loadOriginal(id);
    try {
      Transcript corrected = corrector.correct(transcript);
      if (!corrected.equals(transcript)) {
        transcripts.saveCorrected(id, corrected);
        return corrected;
      }
      return transcript;
    } catch (RuntimeException e) {
      log.warn("전사 교정 실패, 원문으로 진행: conversation={}", id, e);
      return transcripts.load(id);
    }
  }

  /**
   * 모든 구간을 외부 API로 동시에 불러 후보를 모은다. 하나라도 실패하면 null (대기열로 다시 한다). 구간 순서대로 합쳐 대기열로 한 것과 같은 결과를 만든다.
   */
  private StepOutcome allWindows(StepContext context, Transcript transcript) {
    if (!direct.available()) {
      return null;
    }
    String id = context.conversation().publicId();
    List<Integer> windows = windows(transcript);
    List<Callable<JsonNode>> calls = new ArrayList<>();
    for (int w : windows) {
      String prompt = prompt(transcript, w);
      calls.add(
          () ->
              llm.parse(
                  direct
                      .chat(
                          DirectLlm.EXTRACT,
                          Prompts.EXTRACT_SYSTEM.strip(),
                          prompt,
                          Prompts.EXTRACT_SCHEMA)
                      .orElseThrow(() -> new IllegalStateException("외부 LLM 응답 없음"))));
    }
    long began = System.nanoTime();
    List<Optional<JsonNode>> results = Parallel.run(calls, CONCURRENCY, DIRECT_TIME);
    if (results.stream().anyMatch(Optional::isEmpty)) {
      log.warn("장면 추출 동시 호출 중 실패한 구간이 있어 대기열로 다시 한다: conversation={}", id);
      return null;
    }
    store.saveCandidates(id, List.of());
    store.saveNames(id, List.of());
    for (int i = 0; i < windows.size(); i++) {
      absorb(id, transcript, windows.get(i), results.get(i).orElseThrow());
    }
    if (windows.size() == 1 && store.candidates(id).isEmpty()) {
      // 짧은 녹음은 사용자가 만화로 만들려고 고른 장면이다. "특별한 순간이 없으면 빈 목록" 규칙에 걸려 30초짜리 웃긴 대화도
      // 장면 없음으로 끝났다(office-13, office-16). 한 번만 다시 물어 가장 나은 장면 하나를 받는다
      int w = windows.get(0);
      Optional<String> again =
          direct.chat(
              DirectLlm.EXTRACT,
              Prompts.EXTRACT_SYSTEM.strip(),
              prompt(transcript, w) + SHORT_HINT,
              Prompts.EXTRACT_SCHEMA);
      if (again.isPresent()) {
        absorb(id, transcript, w, llm.parse(again.get()));
        log.info("짧은 대화라 장면을 다시 물었다: 후보 {}개, conversation={}", store.candidates(id).size(), id);
      }
    }
    log.info(
        "장면 추출: 구간 {}개를 동시에 처리 {}ms, conversation={}",
        windows.size(),
        (System.nanoTime() - began) / 1_000_000,
        id);
    return new StepOutcome.Done(WorkKeys.candidates(id), Step.SELECT);
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
    absorb(id, transcript, window, content);
    return nextWindow(context, transcript, window);
  }

  /** 구간 하나의 LLM 결과(후보와 실명)를 지금까지 모은 것에 더한다. */
  private void absorb(String id, Transcript transcript, int window, JsonNode content) {
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

  /** 발화가 있는 구간 번호, 차례대로. */
  private List<Integer> windows(Transcript transcript) {
    int last = transcript.durationSec() / properties.windowSec();
    List<Integer> out = new ArrayList<>();
    for (int w = 0; w <= last; w++) {
      if (!part(transcript, w).isEmpty()) {
        out.add(w);
      }
    }
    return out;
  }

  private String prompt(Transcript transcript, int window) {
    int size = properties.windowSec();
    return "대화 언어: "
        + transcript.language()
        + "\n화자: "
        + String.join(", ", transcript.speakers())
        + "\n\n전사 ("
        + (window * size / 60)
        + "분부터):\n"
        + TranscriptText.compact(part(transcript, window));
  }

  private StepOutcome nextWindow(StepContext context, Transcript transcript, int after) {
    for (int w : windows(transcript)) {
      if (w > after) {
        ObjectNode extra = llm.object();
        extra.put("window", w);
        long seed = transcript.seed() + w + context.attempt();
        return llm.job(
            Prompts.EXTRACT_SYSTEM,
            prompt(transcript, w),
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
