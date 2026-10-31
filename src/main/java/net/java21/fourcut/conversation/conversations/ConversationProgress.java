package net.java21.fourcut.conversation.conversations;

import java.util.UUID;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Step;
import org.springframework.stereotype.Component;

/** 파이프라인 진행을 대화 상태에 반영한다. */
@Component
class ConversationProgress implements PipelineObserver {

  private final ConversationRepository conversations;

  ConversationProgress(ConversationRepository conversations) {
    this.conversations = conversations;
  }

  @Override
  public ConversationInfo find(UUID conversationId) {
    return conversations
        .findById(conversationId)
        .map(
            c ->
                new ConversationInfo(c.getId(), c.getOwnerId(), c.getLanguage(), c.getChunkCount()))
        .orElse(null);
  }

  @Override
  public void stepStarted(UUID conversationId, Step step) {
    conversations.findById(conversationId).ifPresent(c -> c.moveTo(ConversationStatus.of(step)));
  }

  @Override
  public void transcribed(UUID conversationId, String language, int durationSec) {
    conversations.findById(conversationId).ifPresent(c -> c.transcribed(language, durationSec));
  }

  @Override
  public void completed(UUID conversationId, UUID comicId, int elapsedSec) {
    conversations.findById(conversationId).ifPresent(c -> c.complete(comicId, elapsedSec));
  }

  @Override
  public void noScene(UUID conversationId) {
    conversations.findById(conversationId).ifPresent(Conversation::noScene);
  }

  @Override
  public void failed(UUID conversationId, String reason) {
    conversations.findById(conversationId).ifPresent(c -> c.fail(reason));
  }
}
