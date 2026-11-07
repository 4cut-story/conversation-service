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
 *     뒤에는 같은 인물의 옷·머리가 컷마다 바뀌어(회차 21) 0.2로 시험했지만 그림이 거의 같고 옷 바뀜도 그대로였다(회차 22). 기본은 0이다
 */
@ConfigurationProperties("comic")
public record ComicProperties(
    @DefaultValue("768") int imageSize,
    @DefaultValue(DEFAULT_STYLE) String style,
    @DefaultValue(DEFAULT_NEGATIVE) String negativePrompt,
    @DefaultValue("http://localhost:8083") String contentUrl,
    @DefaultValue("0.2") double ipEarlyScale,
    @DefaultValue("2") int ipEarlySteps,
    @DefaultValue("0.0") double ipLateBoost) {

  public static final int DEFAULT_IMAGE_SIZE = 768;

  public static final String DEFAULT_STYLE =
      "simple cartoon, thick outlines, flat colors, simple background";

  /**
   * 모든 컷에 거는 negative. guidance 2(CFG)부터 negative가 실제로 먹는다. 회차 23에서 한 편 안에 반실사 컷(solo-01 1컷,
   * twist-08 1·4컷, family-09 3·4컷)과 혼자 흑백인 컷(mystery-08 3컷)이 섞여, 반실사·피부 질감·흑백 말을 더해 그림체를 컷마다 같게
   * 묶는다. 흑백 신문 만화는 합성 단계에서 흑백으로 바꾸므로 그리는 단계에서는 컬러가 맞다.
   */
  public static final String DEFAULT_NEGATIVE =
      "text, letters, watermark, signature, logo, speech bubble, comic panels, multiple panels,"
          + " grid, collage, split screen, picture frame, border, photo, realistic, 3d, cluttered background,"
          + " photorealistic, semi-realistic, detailed skin texture, monochrome, grayscale,"
          + " nsfw, extra fingers, deformed, clones, duplicate person";
}
