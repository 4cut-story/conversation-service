package net.java21.fourcut.conversation.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 작업을 Gemini(무료 등급)로 먼저 처리한다. 서버 안의 워커처럼 작업을 임대하고 결과를 보고한다. Gemini가 실패하거나 한도를 넘기면 임대를 내려놓아 작업이
 * 대기열에 그대로 남고, GPU 워커(로컬 qwen)가 가져간다. 한도 초과나 오류 뒤에는 {@code cooldown} 동안 Gemini를 건너뛴다.
 */
@Component
class GeminiRunner {

  static final String WORKER_ID = "gemini";

  private static final Logger log = LoggerFactory.getLogger(GeminiRunner.class);

  private final GeminiProperties properties;
  private final GpuJobRepository jobs;
  private final GpuJobService service;
  private final PipelineProperties pipeline;
  private final JsonMapper json;
  private final Clock clock;
  private final TaskExecutor executor;
  private final TransactionTemplate tx;
  private final GeminiClient client;
  private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.MIN);

  @Autowired
  GeminiRunner(
      GeminiProperties properties,
      GpuJobRepository jobs,
      GpuJobService service,
      PipelineProperties pipeline,
      JsonMapper json,
      Clock clock,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      TransactionTemplate tx,
      RestClient.Builder builder) {
    this(
        properties,
        jobs,
        service,
        pipeline,
        json,
        clock,
        executor,
        tx,
        properties.enabled() ? GeminiClient.create(builder, properties, json) : null);
  }

  GeminiRunner(
      GeminiProperties properties,
      GpuJobRepository jobs,
      GpuJobService service,
      PipelineProperties pipeline,
      JsonMapper json,
      Clock clock,
      TaskExecutor executor,
      TransactionTemplate tx,
      GeminiClient client) {
    this.properties = properties;
    this.jobs = jobs;
    this.service = service;
    this.pipeline = pipeline;
    this.json = json;
    this.clock = clock;
    this.executor = executor;
    this.tx = tx;
    this.client = client;
  }

  @TransactionalEventListener
  void onQueued(LlmJobQueued event) {
    if (active()) {
      executor.execute(() -> run(event.jobId()));
    }
  }

  boolean active() {
    return client != null && clock.instant().isAfter(pausedUntil.get());
  }

  /** 작업 하나를 Gemini로 처리해 본다. 이미 GPU 워커가 가져갔으면 아무것도 하지 않는다. */
  void run(Long jobId) {
    Optional<Lease> lease = Optional.ofNullable(tx.execute(status -> lease(jobId)));
    lease.ifPresent(this::process);
  }

  private void process(Lease lease) {
    long start = System.nanoTime();
    JsonNode result;
    try {
      result = client.chat(json.readTree(lease.payload()));
    } catch (RuntimeException e) {
      pausedUntil.set(clock.instant().plus(properties.cooldown()));
      log.warn(
          "Gemini 실패, GPU 워커(qwen)로 넘김: job={} reason={} ({} 동안 Gemini 쉼)",
          lease.jobId(),
          e.getClass().getSimpleName() + ": " + e.getMessage(),
          properties.cooldown());
      tx.executeWithoutResult(status -> release(lease));
      return;
    }
    boolean ok =
        Boolean.TRUE.equals(
            tx.execute(status -> service.complete(lease.jobId(), lease.token(), result)));
    log.info(
        "Gemini 처리: job={} model={} ok={} {}ms",
        lease.jobId(),
        properties.model(),
        ok,
        (System.nanoTime() - start) / 1_000_000);
  }

  private Lease lease(Long jobId) {
    GpuJob job =
        jobs.lockById(jobId)
            .filter(j -> j.getState() == GpuJob.State.QUEUED && j.getType() == JobType.LLM)
            .orElse(null);
    if (job == null) {
      return null;
    }
    job.lease(WORKER_ID, clock.instant(), pipeline.leaseTime().plus(properties.timeout()));
    return new Lease(jobId, job.getLeaseToken(), job.getPayload());
  }

  private void release(Lease lease) {
    jobs.lockById(lease.jobId()).filter(j -> j.heldBy(lease.token())).ifPresent(GpuJob::release);
  }

  private record Lease(Long jobId, UUID token, String payload) {}
}
