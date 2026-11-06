package net.java21.fourcut.conversation.pipeline;

import java.util.Optional;

/** 대기열을 거치지 않고 Gemini를 바로 한 번 부른다. 결과가 없어도 되는 보조 작업(전사 교정 등)에 쓴다. 실패하면 GPU 워커로 넘기지 않고 빈 값을 돌려준다. */
public interface DirectLlm {

  /** 지금 부를 수 있는지 (키가 있고, 한도 초과 뒤 쉬는 중이 아니다). */
  boolean available();

  /**
   * @param jsonSchema 응답 JSON 스키마
   * @return 응답 본문(JSON 문자열). 키가 없거나, 쉬는 중이거나, 호출이 실패하면 빈 값
   */
  Optional<String> chat(String system, String prompt, String jsonSchema);
}
