package net.java21.fourcut.conversation.character;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.java21.fourcut.conversation.support.BaseEntity;
import net.java21.fourcut.conversation.support.PublicId;

/** 캐릭터 한 명 (characters). 컨셉·표정마다 다른 모습은 {@link CharacterLook}에 둔다. */
@Entity
@Table(name = "characters")
class StoryCharacter extends BaseEntity {

  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  @Column(nullable = false, length = 30)
  private String name;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(length = 50)
  private String job;

  @Column(length = 10)
  private String gender;

  @Column(name = "age_group", length = 10)
  private String ageGroup;

  @Column(name = "ip_adapter_scale", nullable = false, precision = 3, scale = 2)
  private BigDecimal ipAdapterScale = new BigDecimal("0.60");

  @Column(nullable = false)
  private boolean active = true;

  protected StoryCharacter() {
    // JPA 전용
  }

  StoryCharacter(
      String name, String description, String job, String gender, String ageGroup, double scale) {
    this.name = name;
    this.description = description;
    this.job = job;
    this.gender = gender;
    this.ageGroup = ageGroup;
    this.ipAdapterScale = BigDecimal.valueOf(scale).setScale(2, RoundingMode.HALF_UP);
  }

  String publicId() {
    return publicId;
  }

  String name() {
    return name;
  }

  String description() {
    return description;
  }

  String job() {
    return job;
  }

  String gender() {
    return gender;
  }

  String ageGroup() {
    return ageGroup;
  }

  double scale() {
    return ipAdapterScale.doubleValue();
  }

  boolean active() {
    return active;
  }

  void deactivate() {
    this.active = false;
  }
}
