package net.java21.fourcut.conversation.conversations;

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
  public ConversationInfo find(Long conversationId) {
    return conversations
        .findById(conversationId)
        .map(
            c ->
                new ConversationInfo(
                    c.getId(), c.getPublicId(), c.getOwnerId(), c.getLanguage(), c.getChunkCount()))
        .orElse(null);
  }

  @Override
  public void stepStarted(Long conversationId, Step step) {
    conversations.findById(conversationId).ifPresent(c -> c.moveTo(ConversationStatus.of(step)));
  }

  @Override
  public void transcribed(Long conversationId, String language, int durationSec) {
    conversations.findById(conversationId).ifPresent(c -> c.transcribed(language, durationSec));
  }

  @Override
  public void completed(Long conversationId, String comicId, int elapsedSec) {
    conversations.findById(conversationId).ifPresent(c -> c.complete(comicId, elapsedSec));
  }

  @Override
  public void noScene(Long conversationId) {
    conversations.findById(conversationId).ifPresent(Conversation::noScene);
  }

  @Override
  public void failed(Long conversationId, String reason) {
    conversations.findById(conversationId).ifPresent(c -> c.fail(reason));
  }
}
