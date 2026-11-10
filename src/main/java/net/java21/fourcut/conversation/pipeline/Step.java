package net.java21.fourcut.conversation.pipeline;

/** 파이프라인 단계. 순서대로 진행하고, 단계마다 최대 3번(처음 + 재시도 2번) 시도한다. */
public enum Step {
  TRANSCRIBE,
  EXTRACT,
  SELECT,
  SCRIPT,
  REVIEW,
  DRAW,
  /**
   * 대사 읽기 (라이브 모드 대사 음성). 인물마다 목소리를 정하고 대사마다 감정을 정해 GPU 워커에 읽힌다. 실패하거나 시간을 넘기면 다시 하지 않고 소리 없이 합성으로
   * 넘어간다. 기능 스위치(comic.speech.enabled)가 꺼져 있으면 바로 넘어간다.
   */
  SPEAK,
  COMPOSE,
  /**
   * 녹음 중 구간 전사 (FR-015). 파이프라인 단계가 아니라 녹음하는 동안 GPU 작업으로만 돈다. pipeline_steps에 남기지 않고, 실패해도 대화는 실패하지
   * 않는다 (마무리 전사가 그 부분을 다시 한다).
   */
  TRANSCRIBE_PART
}
