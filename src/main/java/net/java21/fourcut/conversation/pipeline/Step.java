package net.java21.fourcut.conversation.pipeline;

/** 파이프라인 단계. 순서대로 진행하고, 단계마다 최대 3번(처음 + 재시도 2번) 시도한다. */
public enum Step {
  TRANSCRIBE,
  EXTRACT,
  SELECT,
  SCRIPT,
  REVIEW,
  DRAW,
  COMPOSE,
  /**
   * 녹음 중 구간 전사 (FR-015). 파이프라인 단계가 아니라 녹음하는 동안 GPU 작업으로만 돈다. pipeline_steps에 남기지 않고, 실패해도 대화는 실패하지
   * 않는다 (마무리 전사가 그 부분을 다시 한다).
   */
  TRANSCRIBE_PART
}
