package net.java21.fourcut.conversation.rss;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/** 피드에서 본 기사 하나와 그 결과 (004 FR-058). 피드마다 기사 키가 유일해서 같은 기사로 두 번 만들지 않는다. */
@Entity
@Table(name = "rss_feed_items")
class RssFeedItem extends BaseEntity {

  /** 기사 결과. */
  enum Status {
    /** 대화를 시작했다 (또는 시작하는 중). */
    MADE,
    /** 처음 등록한 피드의 옛 기사라 건너뛰었다 (FR-056). */
    SKIPPED,
    /** 최근에 만든 기사와 주제가 비슷해서 건너뛰었다 (FR-068). */
    SIMILAR,
    /** 기사를 읽지 못했다. 다시 시도하지 않는다 (FR-060). */
    FAILED
  }

  @Column(name = "feed_id", nullable = false, updatable = false)
  private Long feedId;

  @Column(name = "item_key", nullable = false, updatable = false, length = 2048)
  private String itemKey;

  @Column(nullable = false, updatable = false, length = 2048)
  private String link;

  @Column(length = 300)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(length = 500)
  private String reason;

  @Column(name = "conversation_id", length = PublicId.LENGTH)
  private String conversationId;

  /** 기사 설명 임베딩 (float 리틀 엔디언). 임베딩 서버를 못 쓰면 null. */
  @Column(columnDefinition = "bytea")
  private byte[] embedding;

  protected RssFeedItem() {
    // JPA 전용
  }

  RssFeedItem(Long feedId, FeedParser.Entry entry, Status status) {
    this.feedId = feedId;
    this.itemKey = entry.key();
    this.link = entry.link();
    this.title = entry.title();
    this.status = status;
  }

  void made(String conversationId) {
    this.status = Status.MADE;
    this.conversationId = conversationId;
  }

  /** 최근 기사와 비슷해서 건너뛴다. */
  void similar(String reason) {
    this.status = Status.SIMILAR;
    this.reason = cut(reason);
  }

  void embedded(float[] vector) {
    if (vector == null) {
      this.embedding = null;
      return;
    }
    java.nio.ByteBuffer buffer =
        java.nio.ByteBuffer.allocate(vector.length * Float.BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    for (float v : vector) {
      buffer.putFloat(v);
    }
    this.embedding = buffer.array();
  }

  /** 저장한 임베딩. 없으면 빈 배열. */
  float[] vector() {
    if (embedding == null) {
      return new float[0];
    }
    java.nio.FloatBuffer floats =
        java.nio.ByteBuffer.wrap(embedding).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    float[] out = new float[floats.remaining()];
    floats.get(out);
    return out;
  }

  private static String cut(String reason) {
    return reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
  }

  void failed(String reason) {
    this.status = Status.FAILED;
    this.reason = cut(reason);
  }

  String getItemKey() {
    return itemKey;
  }

  String getLink() {
    return link;
  }

  String getTitle() {
    return title;
  }

  Status getStatus() {
    return status;
  }

  String getReason() {
    return reason;
  }

  String getConversationId() {
    return conversationId;
  }
}
