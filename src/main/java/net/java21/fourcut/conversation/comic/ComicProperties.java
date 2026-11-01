package net.java21.fourcut.conversation.comic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 그림·합성 설정.
 *
 * @param imageSize 컷 그림 한 변. 단순한 그림이라 768이면 충분하고 1024보다 계산이 약 40% 적다
 * @param style 모든 컷에 붙이는 그림체 프롬프트
 * @param contentUrl content-service 주소 (만화함 등록)
 */
@ConfigurationProperties("comic")
public record ComicProperties(
    @DefaultValue("768") int imageSize,
    @DefaultValue("simple cartoon, thick outlines, flat colors, plain background") String style,
    @DefaultValue(
            "text, letters, watermark, speech bubble, photo, realistic, 3d, detailed background,"
                + " intricate, nsfw, extra fingers, deformed")
        String negativePrompt,
    @DefaultValue("http://localhost:8083") String contentUrl) {}
