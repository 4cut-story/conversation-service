package net.java21.fourcut.conversation.pipeline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.web.HeaderNames;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPU 워커 API. 클러스터 밖 GPU PC가 게이트웨이를 거쳐 부른다. 토큰(GPU_WORKER_TOKEN)은 게이트웨이가 확인하고, 여기서는 {@code
 * X-Worker-Id}만 본다.
 */
@RestController
@Validated
@RequestMapping("/internal/gpu-jobs")
class GpuJobController {

  private static final String WORKER_ID = "^[A-Za-z0-9._-]{1,50}$";

  private final GpuJobService jobs;

  GpuJobController(GpuJobService jobs) {
    this.jobs = jobs;
  }

  /** 작업 하나를 가져간다. 없으면 204. */
  @PostMapping("/claim")
  ResponseEntity<WorkerApi.ClaimResponse> claim(
      @RequestHeader(HeaderNames.WORKER_ID) @Pattern(regexp = WORKER_ID) String workerId,
      @Valid @RequestBody WorkerApi.ClaimRequest request) {
    return jobs.claim(workerId, request)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/{jobId}/heartbeat")
  WorkerApi.HeartbeatResponse heartbeat(
      @RequestHeader(HeaderNames.WORKER_ID) @Pattern(regexp = WORKER_ID) String workerId,
      @PathVariable Long jobId,
      @Valid @RequestBody WorkerApi.HeartbeatRequest request) {
    return jobs.heartbeat(jobId, workerId, request.leaseToken())
        .map(WorkerApi.HeartbeatResponse::new)
        .orElseThrow(GpuJobController::leaseLost);
  }

  @PostMapping("/{jobId}/result")
  ResponseEntity<Void> result(
      @RequestHeader(HeaderNames.WORKER_ID) @Pattern(regexp = WORKER_ID) String workerId,
      @PathVariable Long jobId,
      @Valid @RequestBody WorkerApi.ResultRequest request) {
    if (!jobs.result(jobId, workerId, request)) {
      throw leaseLost();
    }
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{jobId}/fail")
  ResponseEntity<Void> fail(
      @RequestHeader(HeaderNames.WORKER_ID) @Pattern(regexp = WORKER_ID) String workerId,
      @PathVariable Long jobId,
      @Valid @RequestBody WorkerApi.FailRequest request) {
    if (!jobs.fail(jobId, workerId, request)) {
      throw leaseLost();
    }
    return ResponseEntity.ok().build();
  }

  private static ApiException leaseLost() {
    return new ApiException(ErrorCode.LEASE_LOST);
  }
}
