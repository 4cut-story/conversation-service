package net.java21.fourcut.conversation.comic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 그림·합성 설정.
 *
 * @param imageSize 컷 그림 한 변. 단순한 그림이라 768이면 충분하고 1024보다 계산이 약 40% 적다
 * @param style 모든 컷에 붙이는 그림체 프롬프트
 * @param contentUrl content-service 주소 (만화함 등록)
 * @param ipEarlyScale 캐릭터 참조(IP-Adapter)를 앞쪽 step에 거는 최대 강도
 * @param ipEarlySteps 약하게 거는 앞쪽 step 수 (8 step 중). 구도는 프롬프트가 정하게 하고, 그 뒤 step부터 캐릭터 강도 +
 *     ipLateBoost로 건다. 4 step이면 머리 색이 이미 정해진 뒤라 옆 인물의 외모 문구("gray hair")가 번진 것을 참조가 되돌리지
 *     못했다(funny-03: 젊은 손녀가 흰머리로 그려짐). 0이면 스케줄 없이 캐릭터 강도를 그대로 건다
 * @param ipLateBoost 뒤쪽 step에서 캐릭터 강도에 더하는 값. 0.2(캐릭터 0.6 → 0.8)이면 선과 질감을 정하는 뒤쪽 step이 참조 사진을 따라가
 *     일상 웹툰 컷이 Plus 도입 전보다 납작하고 흐려졌다(SDXL 기본 모델). 그림 모델을 DreamShaper XL Lightning(guidance 2)으로 바꾼
 *     뒤에는 글이 세져 같은 인물의 옷·머리가 컷마다 바뀌어(회차 21) 다시 0.2로 둔다
 */
@ConfigurationProperties("comic")
public record ComicProperties(
    @DefaultValue("768") int imageSize,
    @DefaultValue(DEFAULT_STYLE) String style,
    @DefaultValue(DEFAULT_NEGATIVE) String negativePrompt,
    @DefaultValue("http://localhost:8083") String contentUrl,
    @DefaultValue("0.2") double ipEarlyScale,
    @DefaultValue("2") int ipEarlySteps,
    @DefaultValue("0.2") double ipLateBoost) {

  public static final int DEFAULT_IMAGE_SIZE = 768;

  public static final String DEFAULT_STYLE =
      "simple cartoon, thick outlines, flat colors, simple background";

  public static final String DEFAULT_NEGATIVE =
      "text, letters, watermark, signature, logo, speech bubble, comic panels, multiple panels,"
          + " grid, collage, split screen, picture frame, border, photo, realistic, 3d, cluttered background,"
          + " nsfw, extra fingers, deformed, clones, duplicate person";
}
