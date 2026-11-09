package net.java21.fourcut.conversation.conversations;

import jakarta.validation.Valid;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.article.ArticleException;
import net.java21.fourcut.conversation.article.ArticleReader;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.pipeline.GpuStatus;
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
  private final DailyLimit dailyLimit;
  private final GpuStatus gpu;
  private final ArticleReader articles;

  ConversationController(
      ConversationService conversations,
      DailyLimit dailyLimit,
      GpuStatus gpu,
      ArticleReader articles) {
    this.conversations = conversations;
    this.dailyLimit = dailyLimit;
    this.gpu = gpu;
    this.articles = articles;
  }

  private Article read(String url) {
    try {
      return articles.read(url);
    } catch (ArticleException e) {
      throw new ApiException(ErrorCode.ARTICLE_UNREADABLE, e.userMessage(), e);
    }
  }

  /** GPU PC가 켜져 있는지. 꺼져 있으면 녹음은 받아 두고 켜지면 이어서 만든다. */
  @GetMapping("/gpu-status")
  ConversationApi.GpuStatusView gpuStatus(@RequestHeader(HeaderNames.USER_ID) String userId) {
    return new ConversationApi.GpuStatusView(gpu.online());
  }

  @PostMapping
  ResponseEntity<ConversationApi.Created> create(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @RequestHeader(value = HeaderNames.USER_ROLES, required = false) String roles,
      @Valid @RequestBody ConversationApi.CreateRequest body) {
    dailyLimit.check(userId, roles);
    if (body.source() == Conversation.Source.ARTICLE
        && body.url() != null
        && !body.url().isBlank()) {
      // 기사는 트랜잭션 밖에서 읽는다 (최대 십여 초). 읽지 못하면 이유를 바로 알려 준다
      Article article = read(body.url());
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(conversations.createFromArticle(userId, body, article));
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(conversations.create(userId, body));
  }

  @PostMapping("/{id}/chunks")
  ConversationApi.ChunkUpload chunk(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @Valid @RequestBody ConversationApi.ChunkRequest body) {
    return conversations.chunk(userId, id, body);
  }

  @PostMapping("/{id}/finish")
  ResponseEntity<ConversationApi.Finished> finish(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @Valid @RequestBody ConversationApi.FinishRequest body) {
    return ResponseEntity.accepted().body(conversations.finish(userId, id, body.chunkCount()));
  }

  @GetMapping("/{id}")
  ConversationApi.View get(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    return conversations.get(userId, id);
  }

  /** 테스트 전용: 답을 기다리는 수동 LLM 작업. */
  @GetMapping("/{id}/llm-jobs")
  java.util.List<ConversationApi.LlmJob> llmJobs(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    return conversations.llmJobs(userId, id);
  }

  /** 테스트 전용: 수동 LLM 작업에 답을 넣는다. */
  @PostMapping("/{id}/llm-jobs/{jobId}/result")
  ResponseEntity<Void> answerLlmJob(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @PathVariable Long jobId,
      @Valid @RequestBody ConversationApi.LlmAnswer body) {
    conversations.answerLlmJob(userId, id, jobId, body.content());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/candidates")
  JsonNode candidates(@RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    return conversations.candidates(userId, id);
  }

  @GetMapping
  CursorPage<ConversationApi.View> list(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return conversations.list(userId, cursor, size);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    conversations.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
