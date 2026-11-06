package net.java21.fourcut.conversation.character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/**
 * 캐릭터의 컨셉·표정별 모습 (character_looks). 같은 캐릭터라도 조선 컨셉에서는 한복, 무협에서는 도포를 입는다. 설명 임베딩 컬럼은 pgvector가 있을 때만
 * 있어서 JPA로 매핑하지 않고 {@link CharacterVectors}가 다룬다.
 */
@Entity
@Table(name = "character_looks")
class CharacterLook extends BaseEntity {

  /** 콘티 인물을 찾고 참조로 쓰는 기본 표정. */
  static final String NEUTRAL = "NEUTRAL";

  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "character_id", nullable = false, updatable = false)
  private StoryCharacter character;

  @Column(nullable = false, length = 20)
  private String concept;

  @Column(nullable = false, length = 20)
  private String expression;

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

  protected CharacterLook() {
    // JPA 전용
  }

  CharacterLook(
      StoryCharacter character,
      String concept,
      String expression,
      String look,
      String description) {
    this.character = character;
    this.concept = concept;
    this.expression = expression;
    this.look = look;
    this.description = description;
    this.referenceImageKey = CharacterKeys.reference(character.publicId(), publicId);
  }

  String publicId() {
    return publicId;
  }

  StoryCharacter character() {
    return character;
  }

  String concept() {
    return concept;
  }

  String expression() {
    return expression;
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

  /** 외모 문구를 바꾼다. 설명 임베딩은 부르는 쪽이 다시 만든다. */
  void describe(String look, String description) {
    this.look = look;
    this.description = description;
  }

  /** 설명 임베딩을 만든 모델 (임베딩 값은 {@link CharacterVectors}가 쓴다). */
  void embedded(String model) {
    this.embeddingModel = model;
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
}
