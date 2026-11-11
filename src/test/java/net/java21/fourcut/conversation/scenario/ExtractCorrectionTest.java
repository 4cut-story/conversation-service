package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExtractCorrectionTest {

  private static final String ID = "c1";

  private final JsonMapper json = JsonMapper.builder().build();
  private final MemoryStorage storage = new MemoryStorage();
  private final TranscriptStore transcripts = new TranscriptStore(storage, json);
  private final DirectLlm llm = mock(DirectLlm.class);

  /** 교정 뒤 대기열로 장면 추출 (구간 동시 호출 끔). */
  private final ScenarioProperties properties =
      new ScenarioProperties("qwen3:8b", 600, 16384, 0.4, true, false);

  private final ScenarioStore scenarios = new ScenarioStore(storage, json);
  private final ExtractHandler handler = handler(properties);
  private final ExtractHandler parallel =
      handler(new ScenarioProperties("qwen3:8b", 600, 16384, 0.4, true, true));
  private final StepContext context =
      new StepContext(new ConversationInfo(1L, ID, "u", "ko", 1), Step.EXTRACT, 1, null);

  private ExtractHandler handler(ScenarioProperties p) {
    return new ExtractHandler(
        transcripts,
        scenarios,
        new LlmJobs(p, json),
        p,
        new TranscriptCorrector(llm, json),
        llm,
        new net.java21.fourcut.conversation.article.ArticleStore(storage, json));
  }

  private static String candidate(String from, String to, String summary) {
    return "{\"from\":\""
        + from
        + "\",\"to\":\""
        + to
        + "\",\"peak\":\"p\",\"kind\":\"FUNNY\",\"summary\":\""
        + summary
        + "\",\"reason\":\"r\",\"story\":4,\"visual\":4,\"relatable\":4,\"memorable\":4,"
        + "\"intro\":false,\"concept\":\"DAILY\"}";
  }

  /** 0분 구간과 10분 구간에 발화가 있는 전사. */
  private void twoWindows() {
    List<Transcript.Utterance> lines = new java.util.ArrayList<>();
    for (int i = 0; i < 12; i++) {
      lines.add(new Transcript.Utterance(i % 2 == 0 ? "A" : "B", i * 5, i * 5 + 4, "앞 구간 말 " + i));
    }
    for (int i = 0; i < 12; i++) {
      lines.add(
          new Transcript.Utterance(
              i % 2 == 0 ? "A" : "B", 600 + i * 5, 604 + i * 5, "뒤 구간 말 " + i));
    }
    transcripts.save(ID, new Transcript("ko", List.of("A", "B"), lines));
  }

  @Test
  @DisplayName("FR-004: 외부 API를 쓸 수 있으면 10분 구간들을 동시에 불러 후보를 모으고 바로 장면 선택으로 넘어간다")
  void extractsWindowsAtOnce() {
    twoWindows();
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), contains("(0분부터)"), anyString()))
        .thenReturn(
            Optional.of(
                "{\"candidates\":[" + candidate("00:00", "00:55", "앞 장면") + "],\"names\":[]}"));
    when(llm.chat(anyString(), anyString(), contains("(10분부터)"), anyString()))
        .thenReturn(
            Optional.of(
                "{\"candidates\":[" + candidate("10:00", "10:55", "뒤 장면") + "],\"names\":[]}"));
    when(llm.chat(anyString(), anyString(), contains("고칠 전사"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[]}"));

    assertThat(parallel.start(context)).isInstanceOf(StepOutcome.RunLocal.class);
    StepOutcome outcome = parallel.runLocal(context);

    assertThat(outcome).isInstanceOf(StepOutcome.Done.class);
    assertThat(((StepOutcome.Done) outcome).next()).isEqualTo(Step.SELECT);
    assertThat(scenarios.candidates(ID))
        .extracting(SceneCandidate::summary)
        .containsExactly("앞 장면", "뒤 장면");
  }

  @Test
  @DisplayName("FR-004: 구간 동시 호출 중 하나라도 실패하면 예전처럼 첫 구간부터 대기열에 넣는다")
  void fallsBackToQueue() {
    twoWindows();
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), contains("(0분부터)"), anyString()))
        .thenReturn(Optional.of("{\"candidates\":[],\"names\":[]}"));
    when(llm.chat(anyString(), anyString(), contains("(10분부터)"), anyString()))
        .thenReturn(Optional.empty());
    when(llm.chat(anyString(), anyString(), contains("고칠 전사"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[]}"));

    StepOutcome outcome = parallel.runLocal(context);

    assertThat(outcome).isInstanceOf(StepOutcome.Enqueue.class);
    assertThat(((StepOutcome.Enqueue) outcome).payload().path("window").asInt()).isZero();
  }

  @Test
  @DisplayName("FR-003: 테스트 전용 수동 처리 대화는 전사 교정을 대기열 작업으로 두고, 답을 받으면 고친 전사로 장면 고르기 작업을 넣는다")
  void manualCorrection() {
    when(llm.available()).thenReturn(true);
    StepContext manual =
        new StepContext(
            new ConversationInfo(1L, ID, "u", "ko", 1, null, null, true), Step.EXTRACT, 1, null);

    StepOutcome first = parallel.start(manual);
    assertThat(first).isInstanceOf(StepOutcome.Enqueue.class);
    var payload = ((StepOutcome.Enqueue) first).payload();
    assertThat(payload.path("correctFrom").asInt()).isZero();
    assertThat(payload.path("prompt").asString()).contains("고칠 전사").contains("회의 실어");

    StepContext answered = new StepContext(manual.conversation(), Step.EXTRACT, 1, payload);
    StepOutcome next =
        parallel.onResult(
            answered,
            json.createObjectNode().put("content", "{\"fixes\":[{\"i\":0,\"text\":\"회의 싫어\"}]}"));

    assertThat(next).isInstanceOf(StepOutcome.Enqueue.class);
    assertThat(((StepOutcome.Enqueue) next).payload().path("window").asInt()).isZero();
    assertThat(transcripts.load(ID).utterances().get(0).text()).isEqualTo("회의 싫어");
  }

  @BeforeEach
  void setUp() {
    transcripts.save(
        ID,
        new Transcript(
            "ko",
            List.of("A", "B"),
            List.of(
                new Transcript.Utterance("A", 0, 2, "회의 실어"),
                new Transcript.Utterance("B", 3, 5, "나도"))));
  }

  @Test
  @DisplayName("FR-003: Gemini가 있으면 장면 추출 전에 서버에서 전사를 고치고, 장면 추출은 고친 전사를 쓴다")
  void correctsBeforeExtract() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":0,\"text\":\"회의 싫어\"}]}"));

    assertThat(handler.start(context)).isInstanceOf(StepOutcome.RunLocal.class);
    StepOutcome outcome = handler.runLocal(context);

    assertThat(outcome).isInstanceOf(StepOutcome.Enqueue.class);
    String prompt = ((StepOutcome.Enqueue) outcome).payload().path("prompt").asString();
    assertThat(prompt).contains("A: 회의 싫어").doesNotContain("실어");
    assertThat(transcripts.load(ID).utterances().get(0).text()).isEqualTo("회의 싫어");
    // 원본은 따로 남아 다시 시도해도 원본에서 고친다
    assertThat(transcripts.loadOriginal(ID).utterances().get(0).text()).isEqualTo("회의 실어");
    handler.runLocal(context);
    assertThat(transcripts.loadOriginal(ID).utterances().get(0).text()).isEqualTo("회의 실어");
  }

  @Test
  @DisplayName("FR-003: 교정이 실패하면 원문 전사로 장면 추출을 이어 간다")
  void keepsRawOnFailure() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("x"));

    StepOutcome outcome = handler.runLocal(context);

    assertThat(((StepOutcome.Enqueue) outcome).payload().path("prompt").asString())
        .contains("A: 회의 실어");
    assertThat(storage.exists(WorkKeys.rawTranscript(ID))).isFalse();
  }

  @Test
  @DisplayName("Gemini를 쓸 수 없으면 교정 없이 바로 장면 추출 작업을 넣는다")
  void skipsWithoutGemini() {
    when(llm.available()).thenReturn(false);
    assertThat(handler.start(context)).isInstanceOf(StepOutcome.Enqueue.class);
  }

  /** 메모리 객체 저장소. */
  @Test
  @DisplayName("FR-004: 구간이 하나뿐인 짧은 대화에서 후보가 없으면 한 번 더 물어 가장 나은 장면 하나를 받는다")
  void shortTalkAsksAgain() {
    List<Transcript.Utterance> lines = new java.util.ArrayList<>();
    for (int i = 0; i < 6; i++) {
      lines.add(new Transcript.Utterance(i % 2 == 0 ? "A" : "B", i * 4, i * 4 + 3, "짧은 말 " + i));
    }
    transcripts.save(ID, new Transcript("ko", List.of("A", "B"), lines));
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), contains("(0분부터)"), anyString()))
        .thenReturn(Optional.of("{\"candidates\":[],\"names\":[]}"));
    when(llm.chat(anyString(), anyString(), contains("가장 나은 장면 하나"), anyString()))
        .thenReturn(
            Optional.of(
                "{\"candidates\":[" + candidate("00:00", "00:20", "짧은 장면") + "],\"names\":[]}"));
    when(llm.chat(anyString(), anyString(), contains("고칠 전사"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[]}"));

    parallel.start(context);
    StepOutcome outcome = parallel.runLocal(context);

    assertThat(outcome).isInstanceOf(StepOutcome.Done.class);
    assertThat(scenarios.candidates(ID))
        .extracting(SceneCandidate::summary)
        .containsExactly("짧은 장면");
  }

  static class MemoryStorage implements ObjectStorage {
    private final Map<String, byte[]> objects = new HashMap<>();

    @Override
    public String presignGet(String key, Duration ttl) {
      return key;
    }

    @Override
    public String presignPut(String key, String contentType, Duration ttl) {
      return key;
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
      objects.put(key, body);
    }

    @Override
    public Optional<byte[]> get(String key) {
      return Optional.ofNullable(objects.get(key));
    }

    @Override
    public boolean exists(String key) {
      return objects.containsKey(key);
    }

    @Override
    public void deletePrefix(String prefix) {
      objects.keySet().removeIf(k -> k.startsWith(prefix));
    }
  }

  @Test
  @DisplayName("FR-004: 기사로 만든 대화는 각색 기획(흐름, 핵심 사실)을 장면 고르기에 넘기고, 후보의 흐름을 남긴다")
  void articlePlanReachesExtract() {
    List<Transcript.Utterance> lines = new java.util.ArrayList<>();
    for (int i = 0; i < 10; i++) {
      lines.add(new Transcript.Utterance(i % 2 == 0 ? "A" : "B", i * 4, i * 4 + 3.5, "기사 말 " + i));
    }
    transcripts.save(ID, new Transcript("ko", List.of("A", "B"), lines));
    new net.java21.fourcut.conversation.article.ArticleStore(storage, json)
        .savePlan(
            ID,
            new net.java21.fourcut.conversation.article.ArticleDialogue.Plan(
                "풍자", "보여 주기식 대책을 꼬집는다", List.of("기", "승", "전", "결"), List.of("대책 회의만 5번 열렸다")));
    StepContext article =
        new StepContext(
            new ConversationInfo(
                1L,
                ID,
                "u",
                "ko",
                1,
                null,
                null,
                false,
                new ConversationInfo.ArticleSource("https://news.example.com/a", "t", "s")),
            Step.EXTRACT,
            1,
            null);
    when(llm.available()).thenReturn(true);
    String found =
        candidate("00:00", "00:35", "회의만 하는 장면")
            .replace("\"reason\":\"r\"", "\"reason\":\"r\",\"flow\":\"회의만 하는 대책 풍자\"");
    when(llm.chat(
            anyString(), anyString(), contains("기사 핵심 (대사에 꼭 남긴다):\n- 대책 회의만 5번 열렸다"), anyString()))
        .thenReturn(Optional.of("{\"candidates\":[" + found + "],\"names\":[]}"));

    StepOutcome outcome = parallel.runLocal(article);

    assertThat(outcome).isInstanceOf(StepOutcome.Done.class);
    assertThat(scenarios.candidates(ID))
        .extracting(SceneCandidate::flow)
        .containsExactly("회의만 하는 대책 풍자");
  }
}
