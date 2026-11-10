package net.java21.fourcut.conversation.rss;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.article.ArticleException;
import net.java21.fourcut.conversation.article.ArticleReader;
import net.java21.fourcut.conversation.article.FeedDocument;
import net.java21.fourcut.conversation.conversations.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 켜진 피드를 30분마다(설정 {@code rss.check-interval}) 확인한다 (004 FR-055~060). 처음 확인하는 피드는 가장 최근 기사 1편만 만들고
 * 나머지는 건너뛴다. 그 뒤로는 처음 보는 기사를 모두 오래된 것부터 만든다(편수 제한 없음). 기사마다 기록을 먼저 남겨 같은 기사로 두 번 만들지 않는다.
 */
@Component
class FeedChecker {

  static final String START_FAILED = "만화를 시작하지 못했어요.";

  /** 비슷한 기사로 건너뛴 이유. */
  static String similarReason(String title) {
    return "최근에 만든 기사와 주제가 비슷해서 건너뛰었어요" + (title == null ? "." : ": " + title);
  }

  private static final Logger log = LoggerFactory.getLogger(FeedChecker.class);

  private final RssFeedRepository feeds;
  private final RssFeedItemRepository items;
  private final ArticleReader reader;
  private final ConversationService conversations;
  private final SimilarArticles similar;
  private final Clock clock;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

  FeedChecker(
      RssFeedRepository feeds,
      RssFeedItemRepository items,
      ArticleReader reader,
      ConversationService conversations,
      SimilarArticles similar,
      Clock clock) {
    this.feeds = feeds;
    this.items = items;
    this.reader = reader;
    this.conversations = conversations;
    this.similar = similar;
    this.clock = clock;
  }

  /** 켜진 피드를 모두 확인한다. 시작한 대화 수를 돌려준다. */
  @Scheduled(
      initialDelayString = "${rss.initial-delay:2m}",
      fixedDelayString = "${rss.check-interval:30m}")
  public int checkAll() {
    int made = 0;
    for (Long id : feeds.enabledIds()) {
      made += check(id);
    }
    if (made > 0) {
      log.info("RSS 확인: 대화 {}개 시작", made);
    }
    return made;
  }

  /** 뒤에서 바로 확인한다 (등록 직후, "지금 확인"). */
  void checkLater(Long feedId) {
    background.execute(() -> check(feedId));
  }

  /** 피드 하나를 확인한다. 같은 피드를 이미 확인하는 중이면 건너뛴다. 시작한 대화 수를 돌려준다. */
  int check(Long feedId) {
    if (!running.add(feedId)) {
      return 0;
    }
    try {
      return checkNow(feedId);
    } catch (RuntimeException e) {
      log.warn("RSS 확인 오류: feed={}", feedId, e);
      return 0;
    } finally {
      running.remove(feedId);
    }
  }

  private int checkNow(Long feedId) {
    RssFeed feed = feeds.findById(feedId).orElse(null);
    if (feed == null) {
      return 0;
    }
    FeedParser.Feed parsed;
    try {
      FeedDocument doc = reader.fetchFeed(feed.getUrl());
      parsed = FeedParser.parse(doc.body(), doc.url());
    } catch (ArticleException e) {
      feeds.checked(feedId, clock.instant(), e.userMessage());
      return 0;
    }
    List<FeedParser.Entry> fresh = fresh(feedId, parsed.entries());
    List<FeedParser.Entry> toMake;
    if (items.existsByFeedId(feedId)) {
      toMake = oldestFirst(fresh);
    } else {
      FeedParser.Entry latest = latest(fresh);
      toMake = latest == null ? List.of() : List.of(latest);
      fresh.stream()
          .filter(e -> !e.equals(latest))
          .forEach(e -> record(feedId, e, RssFeedItem.Status.SKIPPED));
    }
    feeds.checked(feedId, clock.instant(), null);
    int made = 0;
    for (FeedParser.Entry entry : toMake) {
      if (make(feed, entry)) {
        made++;
      }
    }
    return made;
  }

  /** 처음 보는 기사. */
  private List<FeedParser.Entry> fresh(Long feedId, List<FeedParser.Entry> entries) {
    if (entries.isEmpty()) {
      return new ArrayList<>();
    }
    Set<String> seen =
        new HashSet<>(items.seenKeys(feedId, entries.stream().map(FeedParser.Entry::key).toList()));
    return new ArrayList<>(entries.stream().filter(e -> !seen.contains(e.key())).toList());
  }

  /** 가장 최근 기사: 발행 시각이 가장 늦은 것, 시각이 없으면 피드 맨 앞(보통 최신순). */
  static FeedParser.Entry latest(List<FeedParser.Entry> entries) {
    return entries.stream()
        .filter(e -> e.published() != null)
        .max(Comparator.comparing(FeedParser.Entry::published))
        .orElse(entries.isEmpty() ? null : entries.get(0));
  }

  /** 오래된 것부터: 모두 발행 시각이 있으면 시각 순, 아니면 피드 순서를 뒤집는다(피드는 보통 최신순). */
  static List<FeedParser.Entry> oldestFirst(List<FeedParser.Entry> entries) {
    List<FeedParser.Entry> out = new ArrayList<>(entries);
    if (out.stream().map(FeedParser.Entry::published).allMatch(Objects::nonNull)) {
      out.sort(Comparator.comparing(FeedParser.Entry::published));
    } else {
      java.util.Collections.reverse(out);
    }
    return out;
  }

  /** 기록을 먼저 남긴다. 이미 있으면(다른 확인이 먼저 남김) null. */
  private RssFeedItem record(Long feedId, FeedParser.Entry entry, RssFeedItem.Status status) {
    try {
      return items.saveAndFlush(new RssFeedItem(feedId, entry, status));
    } catch (DataIntegrityViolationException e) {
      return null;
    }
  }

  private boolean make(RssFeed feed, FeedParser.Entry entry) {
    RssFeedItem item = record(feed.getId(), entry, RssFeedItem.Status.MADE);
    if (item == null) {
      return false;
    }
    try {
      Article article = reader.read(entry.link());
      float[] vector = similar.embed(article);
      item.embedded(vector);
      Optional<RssFeedItem> close = similar.find(feed.getOwnerId(), item, article.title(), vector);
      if (close.isPresent()) {
        item.similar(similarReason(close.get().getTitle()));
        items.save(item);
        return false;
      }
      item.made(
          conversations.createFromFeed(
              feed.getOwnerId(),
              feed.getId(),
              feed.isAutoPublish(),
              feed.getConcept(),
              feed.getDialect(),
              article));
    } catch (ArticleException e) {
      item.failed(e.userMessage());
    } catch (RuntimeException e) {
      log.warn("RSS 기사로 대화를 시작하지 못했다: feed={} link={}", feed.getId(), entry.link(), e);
      item.failed(START_FAILED);
    }
    items.save(item);
    return item.getStatus() == RssFeedItem.Status.MADE;
  }
}
