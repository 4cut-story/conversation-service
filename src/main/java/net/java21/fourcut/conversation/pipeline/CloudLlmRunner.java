package net.java21.fourcut.conversation.pipeline;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * LLM 작업을 서버 안에서 외부 API로 먼저 처리한다. 순서는 Claude → Gemini이고, 키가 있는 것만 쓴다. 서버 안의 워커처럼 작업을 임대하고 결과를 보고한다.
 * 모두 실패하거나 쉬는 중이면 임대를 내려놓아 작업이 대기열에 그대로 남고, GPU 워커(로컬 qwen)가 가져간다. 실패한 API는 {@code cooldown} 동안
 * 건너뛴다 (Claude 키가 만료되면 Gemini가 이어 받는다).
 */
@Component
class CloudLlmRunner implements DirectLlm {

  static final String WORKER_ID = "cloud-llm";

  private static final Logger log = LoggerFactory.getLogger(CloudLlmRunner.class);

  private final GpuJobRepository jobs;
  private final GpuJobService service;
  private final PipelineProperties pipeline;
  private final JsonMapper json;
  private final Clock clock;
  private final TaskExecutor executor;
  private final TransactionTemplate tx;
  private final List<Provider> providers;

  @Autowired
  CloudLlmRunner(
      ClaudeProperties claude,
      GeminiProperties gemini,
      GpuJobRepository jobs,
      GpuJobService service,
      PipelineProperties pipeline,
      JsonMapper json,
      Clock clock,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      TransactionTemplate tx,
      RestClient.Builder builder) {
    this(
        jobs, service, pipeline, json, clock, executor, tx, clients(claude, gemini, builder, json));
  }

  CloudLlmRunner(
      GpuJobRepository jobs,
      GpuJobService service,
      PipelineProperties pipeline,
      JsonMapper json,
      Clock clock,
      TaskExecutor executor,
      TransactionTemplate tx,
      List<? extends LlmClient> clients) {
    this.jobs = jobs;
    this.service = service;
    this.pipeline = pipeline;
    this.json = json;
    this.clock = clock;
    this.executor = executor;
    this.tx = tx;
    this.providers = clients.stream().map(Provider::new).toList();
  }

  /** 키가 있는 API를 쓰는 순서대로 (Claude → Gemini). RestClient.Builder는 API마다 복제해 설정이 섞이지 않게 한다. */
  static List<LlmClient> clients(
      ClaudeProperties claude,
      GeminiProperties gemini,
      RestClient.Builder builder,
      JsonMapper json) {
    List<LlmClient> clients = new ArrayList<>();
    if (claude.enabled()) {
      clients.add(ClaudeClient.create(builder.clone(), claude, json));
    }
    if (gemini.enabled()) {
      clients.add(GeminiClient.create(builder.clone(), gemini, json));
    }
    return clients;
  }

  @TransactionalEventListener
  void onQueued(LlmJobQueued event) {
    if (active()) {
      executor.execute(() -> run(event.jobId()));
    }
  }

  /** 지금 부를 수 있는 API가 하나라도 있는지. */
  boolean active() {
    return providers.stream().anyMatch(Provider::active);
  }

  @Override
  public boolean available() {
    return active();
  }

  @Override
  public Optional<String> chat(String system, String prompt, String jsonSchema) {
    ObjectNode job = json.createObjectNode();
    job.put("system", system.strip());
    job.put("prompt", prompt);
    job.set("jsonSchema", json.readTree(jsonSchema));
    job.putObject("options").put("temperature", 0.0);
    for (Provider provider : providers) {
      if (!provider.active()) {
        continue;
      }
      long start = System.nanoTime();
      try {
        String content = provider.client.chat(job).path("content").asString("");
        log.info(
            "{} 직접 호출: model={} {}ms",
            provider.client.name(),
            provider.client.model(),
            (System.nanoTime() - start) / 1_000_000);
        return Optional.of(content);
      } catch (LlmClient.Unavailable e) {
        provider.pause();
        log.warn(
            "{} 직접 호출 쉼: reason={} ({} 동안 쉼)",
            provider.client.name(),
            e.getMessage(),
            provider.client.cooldown());
      } catch (RuntimeException e) {
        log.warn(
            "{} 직접 호출 실패: reason={}",
            provider.client.name(),
            e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
    return Optional.empty();
  }

  /** 작업 하나를 외부 API로 처리해 본다. 이미 GPU 워커가 가져갔으면 아무것도 하지 않는다. */
  void run(Long jobId) {
    Optional<Lease> lease = Optional.ofNullable(tx.execute(status -> lease(jobId)));
    lease.ifPresent(this::process);
  }

  private void process(Lease lease) {
    JsonNode payload = json.readTree(lease.payload());
    for (Provider provider : providers) {
      if (!provider.active()) {
        continue;
      }
      long start = System.nanoTime();
      JsonNode result;
      try {
        result = provider.client.chat(payload);
      } catch (RuntimeException e) {
        provider.pause();
        log.warn(
            "{} 실패, 다음으로 넘김: job={} reason={} ({} 동안 쉼)",
            provider.client.name(),
            lease.jobId(),
            e.getClass().getSimpleName() + ": " + e.getMessage(),
            provider.client.cooldown());
        continue;
      }
      boolean ok =
          Boolean.TRUE.equals(
              tx.execute(status -> service.complete(lease.jobId(), lease.token(), result)));
      log.info(
          "{} 처리: job={} model={} ok={} {}ms",
          provider.client.name(),
          lease.jobId(),
          provider.client.model(),
          ok,
          (System.nanoTime() - start) / 1_000_000);
      return;
    }
    log.warn("외부 LLM API가 모두 실패, GPU 워커(qwen)로 넘김: job={}", lease.jobId());
    tx.executeWithoutResult(status -> release(lease));
  }

  private Lease lease(Long jobId) {
    GpuJob job =
        jobs.lockById(jobId)
            .filter(j -> j.getState() == GpuJob.State.QUEUED && j.getType() == JobType.LLM)
            .orElse(null);
    if (job == null) {
      return null;
    }
    Duration timeouts =
        providers.stream().map(p -> p.client.timeout()).reduce(Duration.ZERO, Duration::plus);
    job.lease(WORKER_ID, clock.instant(), pipeline.leaseTime().plus(timeouts));
    return new Lease(jobId, job.getLeaseToken(), job.getPayload());
  }

  private void release(Lease lease) {
    jobs.lockById(lease.jobId()).filter(j -> j.heldBy(lease.token())).ifPresent(GpuJob::release);
  }

  private record Lease(Long jobId, UUID token, String payload) {}

  /** API 하나와 그 API를 쉬는 시각. */
  private final class Provider {
    private final LlmClient client;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>(Instant.MIN);

    Provider(LlmClient client) {
      this.client = client;
    }

    boolean active() {
      return clock.instant().isAfter(pausedUntil.get());
    }

    void pause() {
      pausedUntil.set(clock.instant().plus(client.cooldown()));
    }
  }
}
