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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class GeminiRunnerTest {

  private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

  private final JsonMapper json = JsonMapper.builder().build();
  private final GeminiProperties properties =
      new GeminiProperties("k", "gemini-x", "u", Duration.ofSeconds(60), Duration.ofMinutes(10));
  private final GpuJobRepository jobs = mock(GpuJobRepository.class);
  private final GpuJobService service = mock(GpuJobService.class);
  private final GeminiClient client = mock(GeminiClient.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private GpuJob job;
  private GeminiRunner runner;

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
    runner = runner(client);
  }

  private GeminiRunner runner(GeminiClient c) {
    return new GeminiRunner(
        properties,
        jobs,
        service,
        new PipelineProperties(
            Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofMinutes(5)),
        json,
        Clock.fixed(NOW, ZoneOffset.UTC),
        Runnable::run,
        tx,
        c);
  }

  @Test
  @DisplayName("대기 중인 LLM 작업을 Gemini가 임대해 처리하고 결과를 보고한다")
  void succeeds() {
    ObjectNode result = json.createObjectNode().put("content", "{}");
    when(client.chat(any())).thenReturn(result);
    when(service.complete(eq(5L), any(UUID.class), eq(result))).thenReturn(true);

    runner.onQueued(new LlmJobQueued(5L));

    assertThat(job.getState()).isEqualTo(GpuJob.State.LEASED);
    assertThat(job.getWorkerId()).isEqualTo(GeminiRunner.WORKER_ID);
    verify(service).complete(5L, job.getLeaseToken(), result);
    assertThat(runner.active()).isTrue();
  }

  @Test
  @DisplayName("Gemini가 실패하면 임대를 내려놓아 GPU 워커(qwen)가 가져가게 하고, 잠시 Gemini를 쉰다")
  void fallsBackToWorker() {
    when(client.chat(any())).thenThrow(new GeminiClient.RateLimited());

    runner.onQueued(new LlmJobQueued(5L));

    assertThat(job.getState()).isEqualTo(GpuJob.State.QUEUED);
    assertThat(job.getLeaseCount()).isZero();
    assertThat(job.getWorkerId()).isNull();
    verify(service, never()).complete(any(), any(), any());
    assertThat(runner.active()).isFalse();

    runner.onQueued(new LlmJobQueued(5L));
    verify(client, times(1)).chat(any());
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

    verify(client, never()).chat(any(JsonNode.class));
    assertThat(job.getWorkerId()).isEqualTo("gpu-pc-1");
  }

  @Test
  @DisplayName("키가 없으면 Gemini를 쓰지 않는다 (모든 LLM 작업은 GPU 워커)")
  void disabledWithoutKey() {
    GeminiRunner off = runner(null);
    off.onQueued(new LlmJobQueued(5L));
    assertThat(off.active()).isFalse();
    assertThat(job.getState()).isEqualTo(GpuJob.State.QUEUED);
  }
}
