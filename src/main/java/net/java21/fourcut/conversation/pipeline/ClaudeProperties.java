package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
 * @param tasks 직접 호출 작업별로 바꿔 쓸 모델과 생각 깊이 (correct, extract, placement). 없으면 model, effort를 쓴다
 */
@ConfigurationProperties("claude")
public record ClaudeProperties(
    @DefaultValue("") String apiKey,
    @DefaultValue("claude-opus-5-5") String model,
    @DefaultValue("medium") String effort,
    @DefaultValue("16000") int maxTokens,
    @DefaultValue("https://api.anthropic.com") String baseUrl,
    @DefaultValue("120s") Duration timeout,
    @DefaultValue("10m") Duration cooldown,
    Map<String, Task> tasks) {

  /** 작업 하나에 쓸 모델과 생각 깊이. 비어 있는 값은 기본값을 쓴다. */
  public record Task(String model, String effort) {}

  @ConstructorBinding
  public ClaudeProperties {
    tasks = tasks == null ? Map.of() : Map.copyOf(tasks);
  }

  public ClaudeProperties(
      String apiKey,
      String model,
      String effort,
      int maxTokens,
      String baseUrl,
      Duration timeout,
      Duration cooldown) {
    this(apiKey, model, effort, maxTokens, baseUrl, timeout, cooldown, Map.of());
  }

  /** 작업에 쓸 모델. */
  public String model(String task) {
    Task t = task == null ? null : tasks.get(task);
    return t == null || t.model() == null || t.model().isBlank() ? model : t.model();
  }

  /** 작업에 쓸 생각 깊이. */
  public String effort(String task) {
    Task t = task == null ? null : tasks.get(task);
    return t == null || t.effort() == null || t.effort().isBlank() ? effort : t.effort();
  }

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }
}
