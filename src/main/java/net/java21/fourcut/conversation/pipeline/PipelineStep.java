package net.java21.fourcut.conversation.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import net.java21.fourcut.conversation.support.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 단계 시도 1건. (대화, 단계, 시도 번호)는 유일하다. */
@Entity
@Table(name = "pipeline_steps")
public class PipelineStep extends BaseEntity {

  /** 시도 상태. */
  public enum Status {
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  @Column(name = "conversation_id", nullable = false)
  private Long conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Step step;

  @Column(nullable = false)
  private short attempt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.RUNNING;

  @Column(name = "runtime_job_id", length = 100)
  private String runtimeJobId;

  @Column(name = "result_key", length = 500)
  private String resultKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model_info", columnDefinition = "json")
  private String modelInfo;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  protected PipelineStep() {
    // JPA 전용
  }

  public PipelineStep(Long conversationId, Step step, int attempt, Instant startedAt) {
    this.conversationId = conversationId;
    this.step = step;
    this.attempt = (short) attempt;
    this.startedAt = startedAt;
  }

  public Long getConversationId() {
    return conversationId;
  }

  public Step getStep() {
    return step;
  }

  public int getAttempt() {
    return attempt;
  }

  public Status getStatus() {
    return status;
  }

  public String getRuntimeJobId() {
    return runtimeJobId;
  }

  public String getResultKey() {
    return resultKey;
  }

  public String getModelInfo() {
    return modelInfo;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  void jobEnqueued(Long jobId) {
    this.runtimeJobId = jobId.toString();
  }

  void modelInfo(String json) {
    this.modelInfo = json;
  }

  void succeed(String resultKey, Instant at) {
    this.status = Status.SUCCEEDED;
    this.resultKey = resultKey;
    this.finishedAt = at;
  }

  void fail(String message, Instant at) {
    this.status = Status.FAILED;
    this.errorMessage = truncate(message, 1000);
    this.finishedAt = at;
  }

  static String truncate(String s, int max) {
    if (s == null) {
      return null;
    }
    return s.length() <= max ? s : s.substring(0, max);
  }
}
