package net.java21.fourcut.conversation.pipeline;

import java.util.UUID;

/**
 * 단계가 쓰는 대화 정보.
 *
 * @param language 사용자가 고른 언어. 없으면 null(자동 감지)
 */
public record ConversationInfo(UUID id, UUID ownerId, String language, int chunkCount) {}
