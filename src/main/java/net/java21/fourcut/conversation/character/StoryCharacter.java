package net.java21.fourcut.conversation.character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/**
 * 캐릭터 한 명 (characters). 설명 임베딩 컬럼은 pgvector가 있을 때만 있어서 JPA로 매핑하지 않고 {@link CharacterVectors}가 다룬다.
 */
@Entity
@Table(name = "characters")
class StoryCharacter extends BaseEntity {

  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  @Column(nullable = false, length = 30)
  private String name;

  /** 이 캐릭터를 쓰는 컨셉. null이면 모든 컨셉. */
  @Column(length = 20)
  private String concept;

  @Column(nullable = false, length = 500)
  private String look;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "embedding_model", length = 50)
  private String embeddingModel;

  @Column(name = "reference_image_key", nullable = false, length = 500)
  private String referenceImageKey;

  @Column(name = "ip_adapter_embed_key", length = 500)
  private String ipAdapterEmbedKey;

  @Column(name = "ip_adapter_model", length = 100)
  private String ipAdapterModel;

  @Column(name = "ip_adapter_scale", nullable = false, precision = 3, scale = 2)
  private BigDecimal ipAdapterScale = new BigDecimal("0.60");

  @Column(nullable = false)
  private boolean active = true;

  protected StoryCharacter() {
    // JPA 전용
  }

  StoryCharacter(String name, String concept, String look, String description, double scale) {
    this.name = name;
    this.concept = concept;
    this.look = look;
    this.description = description;
    this.ipAdapterScale = BigDecimal.valueOf(scale).setScale(2, java.math.RoundingMode.HALF_UP);
    this.referenceImageKey = CharacterKeys.reference(publicId);
  }

  String publicId() {
    return publicId;
  }

  String name() {
    return name;
  }

  String concept() {
    return concept;
  }

  String look() {
    return look;
  }

  String description() {
    return description;
  }

  String embeddingModel() {
    return embeddingModel;
  }

  String referenceImageKey() {
    return referenceImageKey;
  }

  double scale() {
    return ipAdapterScale.doubleValue();
  }

  boolean active() {
    return active;
  }

  /** 지금 어댑터로 계산한 embedding이 있으면 그 객체 키. */
  String embedKeyFor(String adapter) {
    return adapter.equals(ipAdapterModel) ? ipAdapterEmbedKey : null;
  }

  void embedReady(String adapter, String key) {
    this.ipAdapterModel = adapter;
    this.ipAdapterEmbedKey = key;
  }

  /** 참조 그림을 바꾸면 예전 embedding은 쓰지 않는다. */
  void referenceChanged() {
    this.ipAdapterModel = null;
    this.ipAdapterEmbedKey = null;
  }

  void deactivate() {
    this.active = false;
  }
}
