package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import tools.jackson.databind.JsonNode;

/**
 * 서버 안에서 부르는 외부 LLM API 하나(Claude, Gemini). 입력은 워커에 넘기는 LLM 작업과 같고(system, prompt, jsonSchema,
 * options), 결과는 워커와 같은 모양 {@code {content, model, evalCount}}이다.
 */
interface LlmClient {

  /** 로그와 구분에 쓰는 짧은 이름 (claude, gemini). */
  String name();

  String model();

  /** 한 번 호출의 응답 시간 한도. */
  Duration timeout();

  /** 실패한 뒤 이 API를 쉬는 시간. 그동안은 다음 API나 GPU 워커가 한다. */
  Duration cooldown();

  /** LLM 작업 하나를 실행한다. 실패하면 예외 (한도 초과·키 만료는 {@link Unavailable}). */
  JsonNode chat(JsonNode job);

  /** 한도 초과, 과부하, 키 만료·권한 없음처럼 잠시 쉬어야 하는 실패. */
  class Unavailable extends RuntimeException {
    Unavailable(String message) {
      super(message);
    }
  }
}
