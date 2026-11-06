package net.java21.fourcut.conversation.character;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 캐릭터 참조 설정.
 *
 * @param enabled 끄면 콘티 인물을 캐릭터 DB에서 찾지 않는다 (텍스트 외모만으로 그린다)
 * @param maxDistance 설명 임베딩 코사인 거리 한도. 이보다 멀면 비슷한 캐릭터가 없다고 본다
 * @param embeddingUrl OpenAI 호환 임베딩 서버 주소. 비어 있으면 임베딩을 끄고 이름으로만 찾는다
 * @param embeddingModel 설명 임베딩 모델. 1024차원이어야 한다. 바꾸면 기존 임베딩을 다시 만든다
 * @param ipAdapter 워커의 IP-Adapter 이름. embedding 파일 이름이 되고, 바꾸면 embedding을 다시 계산한다
 */
@ConfigurationProperties("characters")
public record CharacterProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("0.35") double maxDistance,
    @DefaultValue("https://emb.java21.net") String embeddingUrl,
    @DefaultValue("bge-m3") String embeddingModel,
    @DefaultValue("plus-face-sdxl-vit-h") String ipAdapter) {

  /** 설명 임베딩 차원 (character_looks.description_embedding vector(1024)). */
  public static final int DIMENSIONS = 1024;
}
