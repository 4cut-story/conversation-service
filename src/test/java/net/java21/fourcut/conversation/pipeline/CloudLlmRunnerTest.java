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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.java21.fourcut.conversation.storage.ObjectStorage;
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
            Duration.ofSeconds(90),
            Duration.ofSeconds(60),
            Duration.ofMinutes(5),
            true,
            Duration.ofMinutes(10)),
        json,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Runnable::run,
        tx,
        clients,
        LlmCache.off(json));
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
  @DisplayName("같은 입력의 LLM 작업과 직접 호출은 처음 받은 답을 다시 쓴다 (시험 세트 짝 비교)")
  void cached() {
    Map<String, byte[]> store = new HashMap<>();
    ObjectStorage storage = mock(ObjectStorage.class);
    org.mockito.Mockito.doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1)))
        .when(storage)
        .put(any(), any(), any());
    when(storage.get(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
    runner =
        new CloudLlmRunner(
            jobs,
            service,
            new PipelineProperties(
                Duration.ofSeconds(90),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                true,
                Duration.ofMinutes(10)),
            json,
            Clock.fixed(NOW, ZoneOffset.UTC),
            Runnable::run,
            tx,
            List.of(claude),
            new LlmCache(storage, json, "claude-model"));
    ObjectNode result = json.createObjectNode().put("content", "{\"a\":1}");
    when(claude.chat(any())).thenReturn(result);
    when(service.complete(any(), any(UUID.class), any())).thenReturn(true);

    runner.onQueued(new LlmJobQueued(5L));
    job = new GpuJob(1L, Step.EXTRACT, JobType.LLM, "{\"prompt\":\"p\"}", NOW);
    when(jobs.lockById(6L)).thenReturn(Optional.of(job));
    runner.onQueued(new LlmJobQueued(6L));

    verify(claude, times(1)).chat(any());
    verify(service, times(2)).complete(any(), any(UUID.class), eq(result));

    assertThat(runner.chat("correct", "s", "q", "{}")).contains("{\"a\":1}");
    assertThat(runner.chat("correct", "s", "q", "{}")).contains("{\"a\":1}");
    verify(claude, times(2)).chat(any());
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
  @DisplayName("서버가 뜨면 넣었지만 못 부른 LLM 작업을 Claude로 처리하고, 다른 서버가 임대한 작업은 뺏지 않는다")
  void resumesQueuedAfterRestart() {
    org.springframework.test.util.ReflectionTestUtils.setField(job, "id", 5L);
    when(jobs.findByTypeAndManualFalseAndStateIn(JobType.LLM, List.of(GpuJob.State.QUEUED)))
        .thenReturn(List.of(job));
    ObjectNode result = json.createObjectNode().put("content", "{}");
    when(claude.chat(any())).thenReturn(result);
    when(service.complete(eq(5L), any(UUID.class), eq(result))).thenReturn(true);

    runner.resumeAfterRestart();

    verify(service).complete(5L, job.getLeaseToken(), result);
    verify(claude, times(1)).chat(any());
  }

  @Test
  @DisplayName("서버가 내려가면 Claude로 처리 중인 대사 작업의 임대를 바로 끝내 다음 서버가 이어 받게 한다")
  void handsOffOnShutdown() {
    UUID[] token = new UUID[1];
    when(claude.chat(any()))
        .thenAnswer(
            i -> {
              token[0] = job.getLeaseToken();
              runner.handOff();
              return json.createObjectNode().put("content", "{}");
            });

    runner.onQueued(new LlmJobQueued(5L));

    assertThat(token[0]).isNotNull();
    assertThat(job.getLeaseExpiresAt()).isBefore(NOW);
    runner.handOff();
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
    assertThat(off.chat("correct", "s", "p", "{}")).isEmpty();
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
    assertThat(runner.chat("correct", "s", "p", "{\"type\":\"object\"}"))
        .contains("{\"fixes\":[]}");
    verify(gemini, never()).chat(any());

    org.mockito.Mockito.reset(claude);
    when(claude.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(claude.chat(any())).thenThrow(new IllegalStateException("Claude HTTP 500"));
    when(gemini.chat(any())).thenReturn(json.createObjectNode().put("content", "{\"g\":1}"));
    assertThat(runner.chat("correct", "s", "p", "{}")).contains("{\"g\":1}");

    // 한도 초과·키 만료는 쉬고, 다른 오류는 쉬지 않는다
    org.mockito.Mockito.reset(claude, gemini);
    when(claude.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(gemini.cooldown()).thenReturn(Duration.ofMinutes(10));
    when(claude.chat(any())).thenThrow(new LlmClient.Unavailable("Claude HTTP 429"));
    when(gemini.chat(any())).thenThrow(new GeminiClient.RateLimited());
    assertThat(runner.chat("correct", "s", "p", "{}")).isEmpty();
    assertThat(runner.available()).isFalse();
    assertThat(runner.chat("correct", "s", "p", "{}")).isEmpty();
    verify(claude, times(1)).chat(any());
    verify(gemini, times(1)).chat(any());
  }
}
