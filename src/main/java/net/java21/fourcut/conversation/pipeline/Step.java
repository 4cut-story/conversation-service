package net.java21.fourcut.conversation.pipeline;

/** 파이프라인 단계. 순서대로 진행하고, 단계마다 최대 3번(처음 + 재시도 2번) 시도한다. */
public enum Step {
  TRANSCRIBE,
  EXTRACT,
  SELECT,
  SCRIPT,
  REVIEW,
  DRAW,
  COMPOSE
}
