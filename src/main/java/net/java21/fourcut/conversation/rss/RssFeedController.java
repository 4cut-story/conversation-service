package net.java21.fourcut.conversation.rss;

import jakarta.validation.Valid;
import java.net.URI;
import net.java21.fourcut.conversation.web.HeaderNames;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RSS 구독 API (common contracts/rss-feeds.yaml). web이 게이트웨이를 거쳐 부른다. */
@RestController
@RequestMapping("/api/v1/rss-feeds")
class RssFeedController {

  private final RssFeedService feeds;

  RssFeedController(RssFeedService feeds) {
    this.feeds = feeds;
  }

  @GetMapping
  RssFeedApi.ListView list(@RequestHeader(HeaderNames.USER_ID) String userId) {
    return feeds.list(userId);
  }

  @PostMapping
  ResponseEntity<RssFeedApi.View> create(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @Valid @RequestBody RssFeedApi.CreateRequest body) {
    RssFeedApi.View view = feeds.create(userId, body);
    return ResponseEntity.created(URI.create("/api/v1/rss-feeds/" + view.id())).body(view);
  }

  @PutMapping("/{id}")
  RssFeedApi.View update(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @Valid @RequestBody RssFeedApi.UpdateRequest body) {
    return feeds.update(userId, id, body);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    feeds.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/check")
  ResponseEntity<RssFeedApi.View> check(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    return ResponseEntity.accepted().body(feeds.check(userId, id));
  }
}
