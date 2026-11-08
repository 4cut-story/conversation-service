package net.java21.fourcut.conversation.pipeline;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 만화 한 편의 제작 정보: 단계마다 걸린 시간·시도 횟수와 작업별 모델·조건(워커가 보낸 모델 정보)을 단계 기록과 GPU 작업에서 모은다. content-service
 * 만화에 그대로 남고 게시물 화면 "제작 정보"에 보인다.
 *
 * <pre>
 * {version: 1, totalMs, steps: [{step, ms, attempts, request?, jobs: [{worker, runMs?, waitMs?, info?}]}],
 *  preTranscribe?: {parts, runMs}}
 * </pre>
 *
 * <p>ms는 단계 시작부터 끝까지(대기 포함), runMs는 워커가 잰 실행 시간(없으면 임대부터 끝까지), waitMs는 대기열에서 기다린 시간이다. request는 단계가
 * 작업을 넣을 때 남긴 정보(LLM 모델·프롬프트 버전·시드, 그림 시드·크기), info는 작업을 처리한 쪽이 보낸 정보다.
 */
@Service
public class Production {

  /** 제작 정보 형식 버전. 모양을 바꾸면 올린다. */
  public static final int VERSION = 1;

  private static final Logger log = LoggerFactory.getLogger(Production.class);

  private final PipelineStepRepository steps;
  private final GpuJobRepository jobs;
  private final PipelineObserver observer;
  private final ObjectStorage storage;
  private final JsonMapper json;
  private final Clock clock;

  Production(
      PipelineStepRepository steps,
      GpuJobRepository jobs,
      PipelineObserver observer,
      ObjectStorage storage,
      JsonMapper json,
      Clock clock) {
    this.steps = steps;
    this.jobs = jobs;
    this.observer = observer;
    this.storage = storage;
    this.json = json;
    this.clock = clock;
  }

  /** 대화 공개 ID와 제작 정보. */
  public record Built(String conversationId, ObjectNode production) {}

  /** 합성까지 끝난(만화가 있는) 대화의 내부 ID. 예전 만화를 채울 때 쓴다. */
  @Transactional(readOnly = true)
  public List<Long> composed() {
    return steps.composedConversations();
  }

  /**
   * 대화의 제작 정보.
   *
   * @param fromResults 모델 정보가 없는 예전 작업이면 저장해 둔 결과에서 답한 모델을 읽는다 (예전 만화를 채울 때만)
   * @return 대화나 단계 기록이 없으면 빈 값
   */
  // 합성 단계의 트랜잭션 안에서도 부르므로, 여기서 난 오류가 만화 등록까지 되돌리지 않게 한다
  @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
  public Optional<Built> of(Long conversationId, boolean fromResults) {
    ConversationInfo conversation = observer.find(conversationId);
    if (conversation == null) {
      return Optional.empty();
    }
    List<PipelineStep> records = steps.findByConversationIdOrderByIdAsc(conversationId);
    if (records.isEmpty()) {
      return Optional.empty();
    }
    Instant now = clock.instant();
    Map<Step, List<GpuJob>> done = new EnumMap<>(Step.class);
    for (GpuJob job :
        jobs.findByConversationIdAndStateOrderByIdAsc(conversationId, GpuJob.State.SUCCEEDED)) {
      done.computeIfAbsent(job.getStep(), s -> new ArrayList<>()).add(job);
    }
    Map<Step, PipelineStep> last = new EnumMap<>(Step.class);
    for (PipelineStep record : records) {
      PipelineStep seen = last.get(record.getStep());
      if (seen == null || seen.getAttempt() < record.getAttempt()) {
        last.put(record.getStep(), record);
      }
    }
    ObjectNode out = json.createObjectNode();
    out.put("version", VERSION);
    Instant began = null;
    Instant ended = null;
    ArrayNode list = out.putArray("steps");
    for (PipelineStep record : last.values()) {
      Instant finished = record.getFinishedAt() == null ? now : record.getFinishedAt();
      if (began == null || record.getStartedAt().isBefore(began)) {
        began = record.getStartedAt();
      }
      if (ended == null || finished.isAfter(ended)) {
        ended = finished;
      }
      ObjectNode node = list.addObject();
      node.put("step", record.getStep().name());
      node.put("ms", millis(record.getStartedAt(), finished));
      node.put("attempts", record.getAttempt());
      JsonNode request = object(record.getModelInfo());
      if (request != null) {
        node.set("request", request);
      }
      ArrayNode jobList = node.putArray("jobs");
      done.getOrDefault(record.getStep(), List.of())
          .forEach(job -> jobList.add(job(job, fromResults)));
    }
    if (began != null) {
      out.put("totalMs", millis(began, ended));
    }
    List<GpuJob> parts = done.getOrDefault(Step.TRANSCRIBE_PART, List.of());
    if (!parts.isEmpty()) {
      ObjectNode pre = out.putObject("preTranscribe");
      pre.put("parts", parts.size());
      pre.put("runMs", parts.stream().mapToLong(Production::runMs).sum());
    }
    return Optional.of(new Built(conversation.publicId(), out));
  }

  private ObjectNode job(GpuJob job, boolean fromResults) {
    ObjectNode node = json.createObjectNode();
    node.put("type", job.getType().name());
    if (job.getWorkerId() != null) {
      node.put("worker", job.getWorkerId());
    }
    node.put("runMs", runMs(job));
    if (job.getStartedAt() != null) {
      node.put("waitMs", millis(job.getEnqueuedAt(), job.getStartedAt()));
    }
    JsonNode info = object(job.getModelInfo());
    if (info == null && fromResults) {
      info = answeredModel(job);
    }
    if (info != null) {
      node.set("info", info);
    }
    return node;
  }

  /** 예전 작업: 저장해 둔 결과의 model(서버에서 처리한 LLM이 답한 모델). 없으면 null. */
  private JsonNode answeredModel(GpuJob job) {
    if (job.getType() != JobType.LLM || job.getResultKey() == null) {
      return null;
    }
    try {
      Optional<byte[]> saved = storage.get(job.getResultKey());
      if (saved.isEmpty()) {
        return null;
      }
      JsonNode result = json.readTree(new String(saved.get(), StandardCharsets.UTF_8));
      if (!result.path("model").isString()) {
        return null;
      }
      return json.createObjectNode().put("model", result.path("model").asString());
    } catch (RuntimeException e) {
      log.debug("결과를 읽지 못했다: job={}", job.getId(), e);
      return null;
    }
  }

  private static long runMs(GpuJob job) {
    if (job.getDurationMs() != null) {
      return job.getDurationMs();
    }
    if (job.getStartedAt() == null || job.getFinishedAt() == null) {
      return 0;
    }
    return millis(job.getStartedAt(), job.getFinishedAt());
  }

  private JsonNode object(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      JsonNode node = json.readTree(raw);
      return node.isObject() ? node : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static long millis(Instant from, Instant to) {
    return Math.max(0, Duration.between(from, to).toMillis());
  }
}
