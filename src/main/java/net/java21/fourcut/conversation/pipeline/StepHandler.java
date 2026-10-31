package net.java21.fourcut.conversation.pipeline;

import tools.jackson.databind.JsonNode;

/** 단계 하나를 맡는 모듈(stt, scenario, comic)이 구현한다. */
public interface StepHandler {

  Step step();

  /** 단계를 시작한다. 보통 GPU 작업을 넣거나 바로 끝낸다. */
  StepOutcome start(StepContext context);

  /** GPU 작업 결과를 받았다. 결과 형식이 틀리면 {@link StepOutcome.Retry}를 돌려준다. */
  default StepOutcome onResult(StepContext context, JsonNode result) {
    throw new UnsupportedOperationException(step() + " 단계는 GPU 작업을 쓰지 않는다");
  }

  /** {@link StepOutcome.RunLocal}로 시작한 단계의 본 작업. 트랜잭션 밖에서 실행된다. */
  default StepOutcome runLocal(StepContext context) {
    throw new UnsupportedOperationException(step() + " 단계는 서버 작업이 없다");
  }
}
