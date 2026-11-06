package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class CloudLlmRunnerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

  private final JsonMapper json = JsonMapper.builder().build();
  private final GpuJobRepository jobs = mock(GpuJobRepository.class);
  private final GpuJobService service = mock(GpuJobService.class);
  private final LlmClient claude = client("claude");
  private final LlmClient gemini = client("gemini");
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private GpuJob job;
  private CloudLlmRunner runner;

  private static LlmClient client(String name) {
    LlmClient c = mock(LlmClient.class);
    when(c.name()).thenReturn(name);
    when(c.model()).thenReturn(name + "-model");
    when(c.timeout()).thenReturn(Duration.ofSeconds(60));
    when(c.cooldown()).thenReturn(Duration.ofMinutes(10));
    return c;
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    when(tx.execute(any()))
        .thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(null));
    org.mockito.Mockito.doAnswer(
            i -> {
              ((Consumer<TransactionStatus>) i.getArgument(0)).accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
    job = new GpuJob(1L, Step.EXTRACT, JobType.LLM, "{\"prompt\":\"p\"}", NOW);
    when(jobs.lockById(5L)).thenReturn(Optional.of(job));
    runner = runner(List.of(claude, gemini));
  }

  private CloudLlmRunner runner(List<LlmClient> clients) {
    return new CloudLlmRunner(
        jobs,
        service,
        new PipelineProperties(
            Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofMinutes(5), true),
        json,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Runnable::run,
        tx,
        clients);
  }

  @Test
  @DisplayName("대기 중인 LLM 작업을 Claude가 먼저 임대해 처리하고 결과를 보고한다 (Gemini는 부르지 않는다)")
  void claudeFirst() {
    ObjectNode result = json.createObjectNode().put("content", "{}");
    when(claude.chat(any())).thenReturn(result);
    when(service.complete(eq(5L), any(UUID.class), eq(result))).thenReturn(true);

    runner.onQueued(new LlmJobQueued(5L));

    assertThat(job.getState()).isEqualTo(GpuJob.State.LEASED);
    assertThat(job.getWorkerId()).isEqualTo(CloudLlmRunner.WORKER_ID);
    verify(service).complete(5L, job.getLeaseToken(), result);
    verify(gemini, never()).chat(any());
    assertThat(runner.active()).isTrue();
  }

  @Test
  @DisplayName("Claude가 실패하면(키 만료 등) 같은 작업을 Gemini가 이어 받고, Claude는 잠시 쉰다")
  void fallsBackToGemini() {
    ObjectNode result = json.createObjectNode().put("content", "{}");
    when(claude.chat(any())).thenThrow(new LlmClient.Unavailable("Claude HTTP 401"));
    when(gemini.chat(any())).thenReturn(result);
    when(service.complete(eq(5L), any(UUID.class), eq(result))).thenReturn(true);

    runner.onQueued(new LlmJobQueued(5L));
    verify(service).complete(5L, job.getLeaseToken(), result);

    job = new GpuJob(1L, Step.SCRIPT, JobType.LLM, "{\"prompt\":\"q\"}", NOW);
    when(jobs.lockById(6L)).thenReturn(Optional.of(job));
    runner.onQueued(new LlmJobQueued(6L));

    verify(claude, times(1)).chat(any());
    verify(gemini, times(2)).chat(any());
    assertThat(runner.active()).isTrue();
  }

  @Test
  @DisplayName("Claude와 Gemini가 모두 실패하면 임대를 내려놓아 GPU 워커(qwen)가 가져가게 한다")
  void fallsBackToWorker() {
    when(claude.chat(any())).thenThrow(new IllegalStateException("Claude HTTP 500"));
    when(gemini.chat(any())).thenThrow(new GeminiClient.RateLimited());

    runner.onQueued(new LlmJobQueued(5L));

    assertThat(job.getState()).isEqualTo(GpuJob.State.QUEUED);
    assertThat(job.getLeaseCount()).isZero();
    assertThat(job.getWorkerId()).isNull();
    verify(service, never()).complete(any(), any(), any());
    assertThat(runner.active()).isFalse();

    runner.onQueued(new LlmJobQueued(5L));
    verify(claude, times(1)).chat(any());
    verify(gemini, times(1)).chat(any());
  }

  @Test
  @DisplayName("GPU 워커가 이미 가져간 작업이나 LLM이 아닌 작업은 건드리지 않는다")
  void skipsTakenJobs() {
    job.lease("gpu-pc-1", NOW, Duration.ofSeconds(90));
    runner.run(5L);
    when(jobs.lockById(6L))
        .thenReturn(Optional.of(new GpuJob(1L, Step.DRAW, JobType.IMAGE, "{}", NOW)));
    runner.run(6L);
    when(jobs.lockById(7L)).thenReturn(Optional.empty());
    runner.run(7L);

    verify(claude, never()).chat(any(JsonNode.class));
    assertThat(job.getWorkerId()).isEqualTo("gpu-pc-1");
  }

  @Test
  @DisplayName("키가 하나도 없으면 외부 API를 쓰지 않는다 (모든 LLM 작업은 GPU 워커)")
  void disabledWithoutKey() {
    CloudLlmRunner off = runner(List.of());
    off.onQueued(new LlmJobQueued(5L));
    assertThat(off.active()).isFalse();
    assertThat(job.getState()).isEqualTo(GpuJob.State.QUEUED);
    assertThat(off.chat("s", "p", "{}")).isEmpty();
  }

  @Test
  @DisplayName("키가 있는 API만 Claude → Gemini 순서로 쓴다")
  void clientsInOrder() {
    ClaudeProperties claudeOn =
        new ClaudeProperties("k", "c", "low", 1, "https://c", Duration.ofSeconds(1), Duration.ZERO);
    ClaudeProperties claudeOff =
        new ClaudeProperties("", "c", "low", 1, "https://c", Duration.ofSeconds(1), Duration.ZERO);
    GeminiProperties geminiOn =
        new GeminiProperties("k", "g", "https://g", Duration.ofSeconds(1), Duration.ZERO);
    GeminiProperties geminiOff =
        new GeminiProperties("", "g", "https://g", Duration.ofSeconds(1), Duration.ZERO);

    assertThat(CloudLlmRunner.clients(claudeOn, geminiOn, RestClient.builder(), json))
        .extracting(LlmClient::name)
        .containsExactly("claude", "gemini");
    assertThat(CloudLlmRunner.clients(claudeOff, geminiOn, RestClient.builder(), json))
        .extracting(LlmClient::name)
        .containsExactly("gemini");
    assertThat(CloudLlmRunner.clients(claudeOff, geminiOff, RestClient.builder(), json)).isEmpty();
  }

  @Test
  @DisplayName("직접 호출: Claude 결과를 돌려주고, Claude가 쉬면 Gemini로, 둘 다 안 되면 빈 값")
  void directChat() {
    when(claude.chat(any())).thenReturn(json.createObjectNode().put("content", "{\"fixes\":[]}"));
    assertThat(runner.available()).isTrue();
    assertThat(runner.chat("s", "p", "{\"type\":\"object\"}")).contains("{\"fixes\":[]}");
    verify(gemini, never()).chat(any());

    org.mockito.Mockito.reset(claude);
    when(claude.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(claude.chat(any())).thenThrow(new IllegalStateException("Claude HTTP 500"));
    when(gemini.chat(any())).thenReturn(json.createObjectNode().put("content", "{\"g\":1}"));
    assertThat(runner.chat("s", "p", "{}")).contains("{\"g\":1}");

    // 한도 초과·키 만료는 쉬고, 다른 오류는 쉬지 않는다
    org.mockito.Mockito.reset(claude, gemini);
    when(claude.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(gemini.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(claude.chat(any())).thenThrow(new LlmClient.Unavailable("Claude HTTP 429"));
    when(gemini.chat(any())).thenThrow(new GeminiClient.RateLimited());
    assertThat(runner.chat("s", "p", "{}")).isEmpty();
    assertThat(runner.available()).isFalse();
    assertThat(runner.chat("s", "p", "{}")).isEmpty();
    verify(claude, times(1)).chat(any());
    verify(gemini, times(1)).chat(any());
  }
}
