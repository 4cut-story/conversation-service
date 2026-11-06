package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Claude로 LLM 작업(장면 추출·콘티·전사 교정)을 가장 먼저 처리하는 설정. 키가 없으면 꺼지고 Gemini, 그다음 GPU 워커(로컬 qwen)가 한다.
 *
 * @param apiKey Claude API 키 (환경 변수 ANTHROPIC_API_KEY). 비어 있으면 끈다
 * @param model Claude 모델 이름
 * @param effort 생각 깊이 (low, medium, high, xhigh, max)
 * @param maxTokens 응답 토큰 한도 (생각 포함)
 * @param timeout 한 번 호출의 응답 시간 한도. 넘기면 다음 API로 넘긴다
 * @param cooldown 실패(한도 초과·키 만료 등) 뒤 Claude를 쉬는 시간
 */
@ConfigurationProperties("claude")
public record ClaudeProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("claude-opus-5-5") String model,
    @DefaultValue("medium") String effort,
    @DefaultValue("16000") int maxTokens,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("120s") Duration timeout,
    @DefaultValue("10m") Duration cooldown) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
