package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gemini로 LLM 작업(장면 추출·콘티)을 먼저 처리하는 설정. 키가 없으면 꺼지고 모든 LLM 작업은 GPU 워커(로컬 qwen)가 한다.
 *
 * @param apiKey Gemini API 키 (환경 변수 GEMINI_API_KEY). 비어 있으면 끈다
 * @param model Gemini 모델 이름
 * @param timeout 한 번 호출의 응답 시간 한도. 넘기면 GPU 워커에 넘긴다
 * @param cooldown 한도 초과(429)나 오류가 난 뒤 Gemini를 쉬는 시간. 그동안은 바로 GPU 워커가 한다
 */
@ConfigurationProperties("gemini")
public record GeminiProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("gemini-3.5-flash-lite") String model,
    @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
    @DefaultValue("60s") Duration timeout,
    @DefaultValue("10m") Duration cooldown) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
