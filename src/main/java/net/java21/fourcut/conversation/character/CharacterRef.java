package net.java21.fourcut.conversation.character;

/**
 * 콘티 인물에 맞춘 캐릭터.
 *
 * @param publicId 캐릭터 모습 공개 ID (컨셉·표정별 그림 하나)
 * @param name 캐릭터 이름
 * @param look 그림용 영어 외모. 참조 그림과 맞도록 콘티 외모 대신 쓴다
 * @param scale IP-Adapter 강도 (0~1)
 * @param referenceImageKey 참조 그림 객체 키
 * @param embedKey 워커가 계산해 둔 embedding 객체 키. 없으면 null
 * @param embedTargetKey embedding이 없을 때 워커가 계산해 올릴 객체 키
 * @param distance 설명 임베딩 거리. 이름으로 찾았으면 0
 * @param costume 캐릭터가 입지 않은 콘티의 역할 옷(군복, 경찰복, 의사 가운…). 있으면 캐릭터 옷 대신 이 옷으로 그리고 참조 그림은 머리만 쓴다. 캐릭터가
 *     이미 그 옷을 입었거나 역할 옷이 없으면 null
 */
public record CharacterRef(
    String publicId,
    String name,
    String look,
    double scale,
    String referenceImageKey,
    String embedKey,
    String embedTargetKey,
    double distance,
    String costume) {

  public CharacterRef(
      String publicId,
      String name,
      String look,
      double scale,
      String referenceImageKey,
      String embedKey,
      String embedTargetKey,
      double distance) {
    this(publicId, name, look, scale, referenceImageKey, embedKey, embedTargetKey, distance, null);
  }

  /** 이 인물을 캐릭터 옷 대신 콘티 역할 옷으로 그리는지. */
  public boolean costumed() {
    return costume != null && !costume.isBlank();
  }

  /** 역할 옷을 입힌 같은 참조. */
  public CharacterRef withCostume(String newCostume) {
    return new CharacterRef(
        publicId,
        name,
        look,
        scale,
        referenceImageKey,
        embedKey,
        embedTargetKey,
        distance,
        newCostume == null || newCostume.isBlank() ? null : newCostume.strip());
  }

  public boolean embedReady() {
    return embedKey != null;
  }
}
