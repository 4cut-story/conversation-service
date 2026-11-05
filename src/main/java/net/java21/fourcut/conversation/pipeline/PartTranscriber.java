package net.java21.fourcut.conversation.pipeline;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** 녹음 중 구간 전사 (FR-015). stt 모듈이 구현한다. 파이프라인은 작업을 넣고 결과를 넘기기만 한다. */
public interface PartTranscriber {

  /**
   * 지금까지 올라온 조각으로 구간 전사 작업 입력을 만든다.
   *
   * @param uploadedChunks 다 올라온 조각 수 (0부터 이어진 조각)
   * @return 작업 입력. 이미 미리 전사한 조각보다 새 조각이 없으면 null
   */
  ObjectNode partPayload(ConversationInfo conversation, int uploadedChunks);

  /** 구간 전사 결과. 형식이 틀리거나 오래된 결과면 버린다. */
  void partDone(ConversationInfo conversation, JsonNode payload, JsonNode result);
}
