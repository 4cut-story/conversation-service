package net.java21.fourcut.conversation.rss;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/** 사용자가 등록한 RSS·Atom 피드 (004 FR-050). 주인만 보고 바꾼다. */
@Entity
@Table(name = "rss_feeds")
class RssFeed extends BaseEntity {

  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  @Column(name = "owner_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String ownerId;

  @Column(nullable = false, updatable = false, length = 2048)
  private String url;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(name = "site_url", length = 2048)
  private String siteUrl;

  @Column(name = "auto_publish", nullable = false)
  private boolean autoPublish;

  /** 그림체. null이면 자동. */
  @Column(length = 20)
  private String concept;

  /** 사투리. null이면 없음. */
  @Column(length = 20)
  private String dialect;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  protected RssFeed() {
    // JPA 전용
  }

  RssFeed(String ownerId, String url, String title, String siteUrl) {
    this.ownerId = ownerId;
    this.url = url;
    this.title = title;
    this.siteUrl = siteUrl;
  }

  /** 설정을 바꾼다. 이미 시작한 대화는 시작할 때의 설정을 따른다 (FR-064). */
  void configure(boolean autoPublish, boolean enabled, String concept, String dialect) {
    this.autoPublish = autoPublish;
    this.enabled = enabled;
    this.concept = concept;
    this.dialect = dialect;
  }

  String getPublicId() {
    return publicId;
  }

  String getOwnerId() {
    return ownerId;
  }

  String getUrl() {
    return url;
  }

  String getTitle() {
    return title;
  }

  String getSiteUrl() {
    return siteUrl;
  }

  boolean isAutoPublish() {
    return autoPublish;
  }

  String getConcept() {
    return concept;
  }

  String getDialect() {
    return dialect;
  }

  boolean isEnabled() {
    return enabled;
  }

  Instant getLastCheckedAt() {
    return lastCheckedAt;
  }

  String getLastError() {
    return lastError;
  }
}
