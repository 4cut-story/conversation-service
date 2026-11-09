package net.java21.fourcut.conversation.comic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.StorySummary;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 줄거리 요약이 생기기 전에 만든 만화에 요약을 채운다. 서버가 뜰 때 한 번 뒤에서 돈다. 콘티가 남아 있는(7일) 만화만 채울 수 있다. 외부 LLM을 만화마다 한 번
 * 부르므로, 한 번 보낸 만화는 표시를 남겨 다시 묻지 않는다. content-service는 비어 있는 만화만 채운다.
 */
@Component
class SummaryBackfill {

  private static final Logger log = LoggerFactory.getLogger(SummaryBackfill.class);

  private final Production production;
  private final PipelineObserver conversations;
  private final StorySummary summaries;
  private final ObjectStorage storage;
  private final ContentClient content;
  private final boolean enabled;

  SummaryBackfill(
      Production production,
      PipelineObserver conversations,
      StorySummary summaries,
      ObjectStorage storage,
      ContentClient content,
      @Value("${comic.summary-backfill:false}") boolean enabled) {
    this.production = production;
    this.conversations = conversations;
    this.summaries = summaries;
    this.storage = storage;
    this.content = content;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    if (enabled) {
      Thread.ofVirtual().name("summary-backfill").start(this::run);
    }
  }

  /** 다시 묻지 않도록 남기는 표시. 콘티와 함께 지워진다. */
  static String doneMark(String id) {
    return WorkKeys.work(id, "summary.done");
  }

  /** 합성까지 끝난 대화마다 줄거리 요약을 채운다. 하나가 실패해도 나머지는 한다. */
  int run() {
    List<Long> ids = production.composed();
    int sent = 0;
    for (Long id : ids) {
      ConversationInfo conversation = conversations.find(id);
      if (conversation == null) {
        continue;
      }
      String publicId = conversation.publicId();
      try {
        if (fill(publicId)) {
          sent++;
        }
      } catch (HttpClientErrorException.NotFound e) {
        log.debug("지운 만화라 건너뜀: conversation={}", publicId);
        mark(publicId);
      } catch (RuntimeException e) {
        log.warn("줄거리 요약 채우기 실패: conversation={} ({})", publicId, e.toString());
      }
    }
    log.info("줄거리 요약 채우기: 대화 {}건 중 {}건 보냄", ids.size(), sent);
    return sent;
  }

  /** 한 만화를 채운다. 보낼 것이 없으면 false. */
  boolean fill(String id) {
    if (storage.exists(doneMark(id))) {
      return false;
    }
    Optional<String> summary = summaries.of(id);
    if (summary.isEmpty()) {
      return false;
    }
    // 보내기에 실패하면(지운 만화 404 말고) 표시를 남기지 않아 다음에 다시 한다
    content.summary(id, new ContentClient.Summary(summary.get()));
    mark(id);
    return true;
  }

  private void mark(String id) {
    storage.put(doneMark(id), "1".getBytes(StandardCharsets.UTF_8), "text/plain");
  }
}
