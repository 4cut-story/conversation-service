package net.java21.fourcut.conversation.conversations;

import jakarta.validation.Valid;
import java.util.UUID;
import net.java21.fourcut.conversation.support.CursorPage;
import net.java21.fourcut.conversation.web.HeaderNames;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 대화 API. web이 게이트웨이를 거쳐 부른다. */
@RestController
@RequestMapping("/api/v1/conversations")
class ConversationController {

  private final ConversationService conversations;

  ConversationController(ConversationService conversations) {
    this.conversations = conversations;
  }

  @PostMapping
  ResponseEntity<ConversationApi.Created> create(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @Valid @RequestBody ConversationApi.CreateRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(userId, body));
  }

  @PostMapping("/{id}/chunks")
  ConversationApi.ChunkUpload chunk(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody ConversationApi.ChunkRequest body) {
    return conversations.chunk(userId, id, body);
  }

  @PostMapping("/{id}/finish")
  ResponseEntity<ConversationApi.Finished> finish(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody ConversationApi.FinishRequest body) {
    return ResponseEntity.accepted().body(conversations.finish(userId, id, body.chunkCount()));
  }

  @GetMapping("/{id}")
  ConversationApi.View get(@RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    return conversations.get(userId, id);
  }

  @GetMapping("/{id}/candidates")
  JsonNode candidates(@RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    return conversations.candidates(userId, id);
  }

  @GetMapping
  CursorPage<ConversationApi.View> list(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return conversations.list(userId, cursor, size);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(
      @RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    conversations.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
