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

  /** 대화를 얻은 방식. ARTICLE은 녹음 대신 기사 URL을 읽어 대화로 각색한다. */
  public enum Source {
    RECORDING,
    UPLOAD,
    ARTICLE
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

  /** 만화 컨셉. null이면 자동. */
  @Column(length = 20)
  private String concept;

  /** 대사 사투리. null이면 사투리 없음. */
  @Column(length = 20)
  private String dialect;

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

  /** 테스트 전용: LLM 작업을 서버가 처리하지 않고 주인이 직접 답을 넣는다. */
  @Column(name = "manual_llm", nullable = false)
  private boolean manualLlm;

  @Column(name = "elapsed_sec")
  private Integer elapsedSec;

  /** 기사로 만든 대화의 원문 주소. 녹음이면 null. */
  @Column(name = "source_url", length = 2048)
  private String sourceUrl;

  @Column(name = "source_title", length = 200)
  private String sourceTitle;

  @Column(name = "source_site", length = 100)
  private String sourceSite;

  /** RSS 구독으로 만든 대화의 구독 내부 ID. 직접 만들었으면 null. 구독을 지워도 남는다. */
  @Column(name = "rss_feed_id")
  private Long rssFeedId;

  /** 완성되면 바로 전체 공개로 게시한다 (RSS 구독의 "바로 공개"). */
  @Column(name = "auto_publish", nullable = false)
  private boolean autoPublish;

  protected Conversation() {
    // JPA 전용
  }

  public Conversation(String ownerId, Source source, String language, Instant consentAt) {
    this(ownerId, source, language, null, consentAt);
  }

  public Conversation(
      String ownerId, Source source, String language, String concept, Instant consentAt) {
    this.ownerId = ownerId;
    this.source = source;
    this.language = language;
    this.concept = concept;
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

  /** 테스트 전용 수동 LLM 처리 대화인지. */
  public boolean isManualLlm() {
    return manualLlm;
  }

  /** 테스트 전용: LLM 작업을 주인이 직접 처리하게 한다. */
  public void markManualLlm() {
    this.manualLlm = true;
  }

  /** 사용자가 고른 대사 사투리. null이면 사투리 없음. */
  public String getDialect() {
    return dialect;
  }

  /** 대사 사투리를 정한다. null이면 사투리 없음. */
  public void chooseDialect(String dialect) {
    this.dialect = dialect;
  }

  /** 사용자가 고른 만화 컨셉. null이면 자동. */
  public String getConcept() {
    return concept;
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

  /** 기사 원문 출처를 남긴다. */
  void fromArticle(String url, String title, String site) {
    this.sourceUrl = url;
    this.sourceTitle = title;
    this.sourceSite = site;
  }

  /** RSS 구독에서 왔다고 남긴다. 바로 공개 여부는 시작할 때의 구독 설정을 따른다 (004 FR-064). */
  void fromFeed(long feedId, boolean publishRightAway) {
    this.rssFeedId = feedId;
    this.autoPublish = publishRightAway;
  }

  public Long getRssFeedId() {
    return rssFeedId;
  }

  public boolean isAutoPublish() {
    return autoPublish;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public String getSourceTitle() {
    return sourceTitle;
  }

  /** 기사 출처. 녹음이면 null. */
  public net.java21.fourcut.conversation.pipeline.ConversationInfo.ArticleSource articleSource() {
    return sourceUrl == null
        ? null
        : new net.java21.fourcut.conversation.pipeline.ConversationInfo.ArticleSource(
            sourceUrl, sourceTitle, sourceSite);
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
