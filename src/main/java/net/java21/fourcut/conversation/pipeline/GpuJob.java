package net.java21.fourcut.conversation.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import net.java21.fourcut.conversation.support.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** GPU 작업 1건. 워커가 가져가(임대) 실행하고 결과나 실패를 보고한다. */
@Entity
@Table(name = "gpu_jobs")
public class GpuJob extends BaseEntity {

  /** 작업 상태. */
  public enum State {
    QUEUED,
    LEASED,
    SUCCEEDED,
    FAILED
  }

  /** 임대는 최대 3번까지 준다. */
  public static final int MAX_LEASES = 3;

  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Step step;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private JobType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state = State.QUEUED;

  /** 입력. 객체 키와 옵션만 둔다. 사전 서명 주소는 가져갈 때 만든다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "json")
  private String payload;

  @Column(name = "result_key", length = 500)
  private String resultKey;

  @Column(name = "lease_count", nullable = false)
  private short leaseCount;

  @Column(name = "lease_token")
  private UUID leaseToken;

  @Column(name = "worker_id", length = 50)
  private String workerId;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "deadline_at")
  private Instant deadlineAt;

  @Column(name = "enqueued_at", nullable = false)
  private Instant enqueuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "error_code", length = 50)
  private String errorCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  protected GpuJob() {
    // JPA 전용
  }

  public GpuJob(Long conversationId, Step step, JobType type, String payload, Instant now) {
    this.conversationId = conversationId;
    this.step = step;
    this.type = type;
    this.payload = payload;
    this.enqueuedAt = now;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public Step getStep() {
    return step;
  }

  public JobType getType() {
    return type;
  }

  public State getState() {
    return state;
  }

  public String getPayload() {
    return payload;
  }

  public int getLeaseCount() {
    return leaseCount;
  }

  public UUID getLeaseToken() {
    return leaseToken;
  }

  public String getWorkerId() {
    return workerId;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public Instant getDeadlineAt() {
    return deadlineAt;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getResultKey() {
    return resultKey;
  }

  /** 지금 임대가 이 토큰의 것인지. */
  boolean heldBy(UUID token) {
    return state == State.LEASED && token != null && token.equals(leaseToken);
  }

  void lease(String worker, Instant now, java.time.Duration leaseTime) {
    this.state = State.LEASED;
    this.leaseCount++;
    this.leaseToken = UUID.randomUUID();
    this.workerId = worker;
    this.leaseExpiresAt = now.plus(leaseTime);
    this.deadlineAt = now.plus(type.maxRun());
    if (startedAt == null) {
      this.startedAt = now;
    }
  }

  void extend(Instant now, java.time.Duration leaseTime) {
    this.leaseExpiresAt = now.plus(leaseTime);
  }

  void requeue() {
    this.state = State.QUEUED;
    this.leaseToken = null;
    this.workerId = null;
    this.leaseExpiresAt = null;
  }

  /** 임대를 내려놓는다 (서버 쪽 Gemini가 실패). 임대 횟수에 세지 않아 GPU 워커는 원래 횟수만큼 시도한다. */
  void release() {
    requeue();
    if (leaseCount > 0) {
      leaseCount--;
    }
  }

  void succeed(String resultKey, Instant now) {
    this.state = State.SUCCEEDED;
    this.resultKey = resultKey;
    this.finishedAt = now;
  }

  void fail(String code, String message, Instant now) {
    this.state = State.FAILED;
    this.errorCode = PipelineStep.truncate(code, 50);
    this.errorMessage = PipelineStep.truncate(message, 1000);
    this.finishedAt = now;
  }
}
