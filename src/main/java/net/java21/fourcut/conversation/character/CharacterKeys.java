package net.java21.fourcut.conversation.character;

/** 캐릭터 객체 키. 대화 작업물(pipeline-work/)과 달리 지우지 않고 계속 쓴다. 모습마다 폴더를 둔다. */
final class CharacterKeys {

  private CharacterKeys() {}

  static String prefix(String characterId, String lookId) {
    return "characters/" + characterId + "/looks/" + lookId + "/";
  }

  /** 참조 그림 (PNG). */
  static String reference(String characterId, String lookId) {
    return prefix(characterId, lookId) + "reference.png";
  }

  /** 워커가 참조 그림으로 계산한 IP-Adapter embedding (safetensors, 키 "embeds"). */
  static String embed(String characterId, String lookId, String adapter) {
    return prefix(characterId, lookId) + "ip-adapter/" + adapter + ".safetensors";
  }
}
