package net.java21.fourcut.conversation.pipeline;

import tools.jackson.databind.node.ObjectNode;

/** 단계 처리 결과. 파이프라인 엔진이 받아 다음 일을 정한다. */
public sealed interface StepOutcome {

  /**
   * GPU 작업을 넣는다. payload의 {@code *GetKey(s)}, {@code *PutKey}는 가져갈 때 사전 서명 주소로 바뀐다.
   *
   * @param modelInfo 단계 기록에 남길 모델·프롬프트 정보 (JSON). 없으면 null
   */
  record Enqueue(JobType type, ObjectNode payload, String modelInfo) implements StepOutcome {}

  /** 서버에서 시간이 걸리는 일을 따로 실행한다 ({@link StepHandler#runLocal}). */
  record RunLocal() implements StepOutcome {}

  /**
   * 단계 성공.
   *
   * @param next 다음 단계. 마지막이면 null
   */
  record Done(String resultKey, Step next) implements StepOutcome {}

  /** 만화 완성 (마지막 단계). */
  record Completed(String resultKey, String comicId) implements StepOutcome {}

  /** 장면을 찾지 못해 끝낸다. */
  record NoScene(String resultKey) implements StepOutcome {}

  /** 이번 시도 실패. 남은 시도가 있으면 같은 단계를 다시 한다. */
  record Retry(String reason) implements StepOutcome {}
}
