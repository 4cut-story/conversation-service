package net.java21.fourcut.conversation.scenario;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 장면·콘티 단계 설정.
 *
 * @param llmModel 워커가 Ollama에 넘길 모델 이름
 * @param windowSec 장면 후보를 뽑는 구간 길이 (10분)
 * @param numCtx Ollama 컨텍스트 길이. 10분 구간 전사가 들어간다
 * @param correctTranscript 장면 추출 전에 외부 LLM API(Claude → Gemini)로 전사의 잘못 들은 말을 고친다. 두 키가 모두 없으면 하지
 *     않는다
 * @param parallelExtract 외부 LLM API를 쓸 수 있으면 장면 추출의 10분 구간들을 대기열 없이 동시에 부른다. 하나라도 실패하면 예전처럼 구간마다
 *     작업을 넣는다 (GPU 워커 qwen으로 넘어갈 수 있게)
 */
@ConfigurationProperties("scenario")
public record ScenarioProperties(
    @DefaultValue("qwen3:8b") String llmModel,
    @DefaultValue("600") int windowSec,
    @DefaultValue("16384") int numCtx,
    @DefaultValue("0.4") double temperature,
    @DefaultValue("true") boolean correctTranscript,
    @DefaultValue("true") boolean parallelExtract) {}
