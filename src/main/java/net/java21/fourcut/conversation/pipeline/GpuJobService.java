package net.java21.fourcut.conversation.pipeline;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * GPU 작업 대기열. 워커가 가져가고(임대), heartbeat로 연장하고, 결과나 실패를 보고한다. 임대가 끝났는데 소식이 없으면 대기열로 되돌린다.
 *
 * <p>임대를 잃은 요청은 상태를 바꾸지 않고 빈 값(또는 false)을 돌려준다. 컨트롤러가 409 LEASE_LOST로 답한다.
 */
@Service
class GpuJobService {

  private static final Logger log = LoggerFactory.getLogger(GpuJobService.class);

  private final GpuJobRepository jobs;
  private final GpuWorkerRepository workers;
  private final PipelineService pipeline;
  private final PayloadResolver resolver;
  private final ObjectStorage storage;
  private final PipelineProperties properties;
  private final JsonMapper json;
  private final Clock clock;

  GpuJobService(
      GpuJobRepository jobs,
      GpuWorkerRepository workers,
      PipelineService pipeline,
      PayloadResolver resolver,
      ObjectStorage storage,
      PipelineProperties properties,
      JsonMapper json,
      Clock clock) {
    this.jobs = jobs;
    this.workers = workers;
    this.pipeline = pipeline;
    this.resolver = resolver;
    this.storage = storage;
    this.properties = properties;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public Optional<WorkerApi.ClaimResponse> claim(String workerId, WorkerApi.ClaimRequest request) {
    Instant now = clock.instant();
    seen(workerId, now, request.loadedModel(), request.version());
    JobType preferred = JobType.forModel(request.loadedModel());
    return jobs.lockNext(
            request.types().stream().map(Enum::name).toList(),
            preferred == null ? "" : preferred.name())
        .map(
            job -> {
              job.lease(workerId, now, properties.leaseTime());
              JsonNode input =
                  resolver.resolve(job.getPayload(), Duration.between(now, job.getDeadlineAt()));
              return new WorkerApi.ClaimResponse(
                  String.valueOf(job.getId()),
                  job.getType(),
                  job.getLeaseToken(),
                  job.getLeaseExpiresAt(),
                  job.getDeadlineAt(),
                  input);
            });
  }

  @Transactional
  public Optional<Instant> heartbeat(Long jobId, String workerId, UUID leaseToken) {
    Instant now = clock.instant();
    seen(workerId, now, null, null);
    GpuJob job = jobs.lockById(jobId).filter(j -> j.heldBy(leaseToken)).orElse(null);
    if (job == null) {
      return Optional.empty();
    }
    if (now.isAfter(job.getDeadlineAt())) {
      job.fail("DEADLINE_EXCEEDED", "최대 실행 시간을 넘겼다", now);
      pipeline.jobFailed(job);
      return Optional.empty();
    }
    job.extend(now, properties.leaseTime());
    return Optional.of(job.getLeaseExpiresAt());
  }

  /** 결과 보고. 같은 토큰으로 다시 보내면 그대로 성공이다. */
  @Transactional
  public boolean result(Long jobId, String workerId, WorkerApi.ResultRequest request) {
    Instant now = clock.instant();
    seen(workerId, now, null, null);
    GpuJob job = jobs.lockById(jobId).orElse(null);
    if (job == null) {
      return false;
    }
    if (job.getState() == GpuJob.State.SUCCEEDED
        && request.leaseToken().equals(job.getLeaseToken())) {
      return true;
    }
    if (!job.heldBy(request.leaseToken())) {
      return false;
    }
    JsonNode result = request.result() == null ? json.createObjectNode() : request.result();
    if (request.modelInfo() != null && request.modelInfo().isObject()) {
      job.report(json.writeValueAsString(request.modelInfo()), request.durationMs());
    } else {
      job.report(null, request.durationMs());
    }
    return succeed(job, result, now);
  }

  /** 수동 LLM 작업을 처리한 것으로 남기는 워커 이름. */
  static final String MANUAL_WORKER = "manual-llm";

  /** 수동 처리를 기다리는 LLM 작업 (테스트 전용). */
  record ManualJob(Long jobId, Step step, String payload) {}

  /** 대화의 수동 LLM 작업 중 답을 기다리는 것 (테스트 전용). */
  @Transactional(readOnly = true)
  List<ManualJob> manualJobs(Long conversationId) {
    return jobs
        .findByConversationIdAndStateAndManualTrue(conversationId, GpuJob.State.QUEUED)
        .stream()
        .map(j -> new ManualJob(j.getId(), j.getStep(), j.getPayload()))
        .toList();
  }

  /**
   * 수동 LLM 작업에 답을 넣는다 (테스트 전용). 결과는 서버·워커가 처리한 것과 같은 모양 {@code {content, model}}으로 남기고 다음 단계로 넘긴다.
   *
   * @return 그 대화의 기다리는 수동 작업이 아니면 false
   */
  @Transactional
  boolean answerManual(Long conversationId, Long jobId, String content) {
    GpuJob job =
        jobs.lockById(jobId)
            .filter(
                j ->
                    j.isManual()
                        && j.getState() == GpuJob.State.QUEUED
                        && j.getConversationId().equals(conversationId))
            .orElse(null);
    if (job == null) {
      return false;
    }
    Instant now = clock.instant();
    job.lease(MANUAL_WORKER, now, properties.leaseTime());
    return succeed(
        job, json.createObjectNode().put("content", content).put("model", "manual"), now);
  }

  /** 서버 안에서 처리한 작업(Claude·Gemini)의 결과. 임대를 잃었으면 false. 워커 연락 시각은 바꾸지 않는다. */
  @Transactional
  public boolean complete(Long jobId, UUID leaseToken, JsonNode result) {
    GpuJob job = jobs.lockById(jobId).filter(j -> j.heldBy(leaseToken)).orElse(null);
    return job != null && succeed(job, result, clock.instant());
  }

  private boolean succeed(GpuJob job, JsonNode result, Instant now) {
    ConversationInfo conversation = pipeline.conversation(job.getConversationId());
    if (conversation == null) {
      return false;
    }
    String key = WorkKeys.work(conversation.publicId(), "jobs/" + job.getId() + ".json");
    storage.put(
        key, json.writeValueAsString(result).getBytes(StandardCharsets.UTF_8), "application/json");
    job.succeed(key, now);
    if (job.getModelInfo() == null && result.path("model").isString()) {
      // 서버에서 처리한 LLM(Claude·Gemini·수동)은 답한 모델을 남긴다
      job.report(
          json.writeValueAsString(
              json.createObjectNode().put("model", result.path("model").asString())),
          job.getDurationMs());
    }
    pipeline.jobSucceeded(job, result);
    return true;
  }

  @Transactional
  public boolean fail(Long jobId, String workerId, WorkerApi.FailRequest request) {
    Instant now = clock.instant();
    seen(workerId, now, null, null);
    GpuJob job = jobs.lockById(jobId).filter(j -> j.heldBy(request.leaseToken())).orElse(null);
    if (job == null) {
      return false;
    }
    // 워커가 보낸 값은 로그에 넣지 않는다 (줄바꿈 주입 방지). 코드와 내용은 gpu_jobs에 남는다
    if (request.retryable() && job.getLeaseCount() < GpuJob.MAX_LEASES) {
      job.requeue();
    } else {
      job.fail(request.code(), request.message(), now);
      pipeline.jobFailed(job);
    }
    return true;
  }

  /** 임대가 끝났거나 마감을 넘긴 작업을 정리한다 (30초마다). */
  @Scheduled(fixedDelayString = "${pipeline.recovery-interval:30s}")
  @Transactional
  public void recover() {
    Instant now = clock.instant();
    for (GpuJob job : jobs.lockExpired(now)) {
      if (now.isAfter(job.getDeadlineAt())) {
        job.fail("DEADLINE_EXCEEDED", "최대 실행 시간을 넘겼다", now);
        pipeline.jobFailed(job);
      } else if (job.getLeaseCount() >= GpuJob.MAX_LEASES) {
        job.fail("LEASE_EXHAUSTED", "임대를 3번 줬지만 끝나지 않았다", now);
        pipeline.jobFailed(job);
      } else {
        log.info("임대 만료로 대기열에 되돌림: job={} worker={}", job.getId(), job.getWorkerId());
        job.requeue();
      }
    }
  }

  private void seen(String workerId, Instant now, String loadedModel, String version) {
    GpuWorker worker =
        workers
            .findByWorkerId(workerId)
            .orElseGet(() -> workers.save(new GpuWorker(workerId, now)));
    worker.seen(now, loadedModel, version);
  }
}
