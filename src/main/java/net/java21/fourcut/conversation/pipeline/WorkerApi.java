package net.java21.fourcut.conversation.pipeline;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** GPU 워커 API의 요청·응답 본문 (docs specs/001 contracts/internal-api.md). */
final class WorkerApi {

  private WorkerApi() {}

  /**
   * 작업 가져가기.
   *
   * @param loadedModel 워커가 지금 올려 둔 모델 묶음 (WHISPER, LLM, SDXL). 같은 종류 작업을 먼저 준다
   */
  record ClaimRequest(
      @NotEmpty List<JobType> types, String loadedModel, @Size(max = 50) String version) {}

  record ClaimResponse(
      UUID jobId,
      JobType type,
      UUID leaseToken,
      Instant leaseExpiresAt,
      Instant deadlineAt,
      JsonNode input) {}

  record HeartbeatRequest(@NotNull UUID leaseToken, Integer progress) {}

  record HeartbeatResponse(Instant leaseExpiresAt) {}

  record ResultRequest(
      @NotNull UUID leaseToken, JsonNode result, JsonNode modelInfo, Long durationMs) {}

  record FailRequest(
      @NotNull UUID leaseToken,
      @NotBlank @Size(max = 50) String code,
      @Size(max = 1000) String message,
      boolean retryable) {}
}
