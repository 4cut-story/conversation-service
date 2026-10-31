package net.java21.fourcut.conversation.scenario;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장면·콘티 단계 설정.
 *
 * @param llmModel 워커가 Ollama에 넘길 모델 이름
 * @param windowSec 장면 후보를 뽑는 구간 길이 (10분)
 * @param numCtx Ollama 컨텍스트 길이. 10분 구간 전사가 들어간다
 */
@ConfigurationProperties("scenario")
public record ScenarioProperties(
    @DefaultValue("qwen3:8b") String llmModel,
    @DefaultValue("600") int windowSec,
    @DefaultValue("16384") int numCtx,
    @DefaultValue("0.4") double temperature) {}
