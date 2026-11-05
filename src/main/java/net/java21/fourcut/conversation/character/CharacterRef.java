package net.java21.fourcut.conversation.character;

/**
 * 콘티 인물에 맞춘 캐릭터.
 *
 * @param publicId 캐릭터 공개 ID
 * @param name 캐릭터 이름
 * @param look 그림용 영어 외모. 참조 그림과 맞도록 콘티 외모 대신 쓴다
 * @param scale IP-Adapter 강도 (0~1)
 * @param referenceImageKey 참조 그림 객체 키
 * @param embedKey 워커가 계산해 둔 embedding 객체 키. 없으면 null
 * @param embedTargetKey embedding이 없을 때 워커가 계산해 올릴 객체 키
 * @param distance 설명 임베딩 거리. 이름으로 찾았으면 0
 */
public record CharacterRef(
    String publicId,
    String name,
    String look,
    double scale,
    String referenceImageKey,
    String embedKey,
    String embedTargetKey,
    double distance) {

  public boolean embedReady() {
    return embedKey != null;
  }
}
