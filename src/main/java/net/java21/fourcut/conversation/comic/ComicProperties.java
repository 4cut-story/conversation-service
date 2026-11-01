package net.java21.fourcut.conversation.comic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 그림·합성 설정.
 *
 * @param imageSize 컷 그림 한 변 (SDXL 기본 1024. VRAM이 모자라면 896)
 * @param style 모든 컷에 붙이는 그림체 프롬프트
 * @param contentUrl content-service 주소 (만화함 등록)
 */
@ConfigurationProperties("comic")
public record ComicProperties(
    @DefaultValue("1024") int imageSize,
    @DefaultValue("korean webtoon style, clean line art, flat colors, simple background")
        String style,
    @DefaultValue(
            "text, letters, watermark, speech bubble, photo, realistic, nsfw, extra fingers, deformed")
        String negativePrompt,
    @DefaultValue("http://localhost:8083") String contentUrl) {}
