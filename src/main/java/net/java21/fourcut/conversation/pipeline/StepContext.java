package net.java21.fourcut.conversation.pipeline;

import tools.jackson.databind.JsonNode;

/**
 * 단계 실행 정보.
 *
 * @param attempt 시도 번호 (1~3)
 * @param jobPayload 결과를 받은 GPU 작업의 입력. 단계를 시작할 때는 null
 */
public record StepContext(
    ConversationInfo conversation, Step step, int attempt, JsonNode jobPayload) {}
