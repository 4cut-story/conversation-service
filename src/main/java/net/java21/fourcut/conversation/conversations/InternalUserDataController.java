package net.java21.fourcut.conversation.conversations;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** auth-service → conversation-service 내부 API. 탈퇴 정리. */
@RestController
class InternalUserDataController {

  private final ConversationService conversations;

  InternalUserDataController(ConversationService conversations) {
    this.conversations = conversations;
  }

  @DeleteMapping("/internal/users/{userId}/data")
  ResponseEntity<Void> delete(@PathVariable String userId) {
    conversations.deleteAllOf(userId);
    return ResponseEntity.noContent().build();
  }
}
