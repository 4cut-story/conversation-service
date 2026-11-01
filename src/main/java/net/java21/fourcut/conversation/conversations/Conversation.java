package net.java21.fourcut.conversation.conversations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/** 대화 1건 (녹음 또는 업로드). */
@Entity
@Table(name = "conversations")
public class Conversation extends BaseEntity {

  /** 녹음 방식. */
  public enum Source {
    RECORDING,
    UPLOAD
  }

  /** 밖에 보이는 대화 ID. API 경로, MinIO 객체 키, content-service에 넘기는 값이다. */
  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  /** 소유자의 공개 ID (auth). */
  @Column(name = "owner_id", nullable = false, length = PublicId.LENGTH)
  private String ownerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Source source;

  @Column(name = "consent_at", nullable = false)
  private Instant consentAt;

  @Column(length = 2)
  private String language;

  @Column(name = "duration_sec")
  private Integer durationSec;

  @Column(name = "chunk_count", nullable = false)
  private int chunkCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConversationStatus status = ConversationStatus.RECORDING;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

  /** 완성된 만화의 공개 ID (content). */
  @Column(name = "comic_id", length = PublicId.LENGTH)
  private String comicId;

  @Column(name = "elapsed_sec")
  private Integer elapsedSec;

  protected Conversation() {
    // JPA 전용
  }

  public Conversation(String ownerId, Source source, String language, Instant consentAt) {
    this.ownerId = ownerId;
    this.source = source;
    this.language = language;
    this.consentAt = consentAt;
  }

  public String getPublicId() {
    return publicId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public Source getSource() {
    return source;
  }

  public String getLanguage() {
    return language;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public int getChunkCount() {
    return chunkCount;
  }

  public ConversationStatus getStatus() {
    return status;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public String getComicId() {
    return comicId;
  }

  public Integer getElapsedSec() {
    return elapsedSec;
  }

  void queue(int chunks) {
    this.chunkCount = chunks;
    this.status = ConversationStatus.QUEUED;
  }

  void moveTo(ConversationStatus next) {
    if (!status.isFinal()) {
      this.status = next;
    }
  }

  void transcribed(String detectedLanguage, int seconds) {
    if (detectedLanguage != null && detectedLanguage.matches("ko|en")) {
      this.language = detectedLanguage;
    }
    this.durationSec = Math.min(Math.max(seconds, 0), 3600);
  }

  void complete(String comic, int elapsed) {
    this.status = ConversationStatus.DONE;
    this.comicId = comic;
    this.elapsedSec = elapsed;
  }

  void noScene() {
    this.status = ConversationStatus.NO_SCENE;
    this.failureReason = "만화로 만들 장면을 찾지 못했어요.";
  }

  void fail(String reason) {
    this.status = ConversationStatus.FAILED;
    this.failureReason = reason;
  }
}
