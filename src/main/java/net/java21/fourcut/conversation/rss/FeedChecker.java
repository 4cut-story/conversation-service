package net.java21.fourcut.conversation.rss;

import java.time.Clock;
import java.time.Duration;
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
import net.java21.fourcut.conversation.article.ExcludedArticle;
import net.java21.fourcut.conversation.article.FeedDocument;
import net.java21.fourcut.conversation.conversations.ConversationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 켜진 피드를 30분마다(설정 {@code rss.check-interval}) 확인한다 (004 FR-055~060). 처음 확인하는 피드는 가장 최근 기사 1편만 만들고
 * 나머지는 건너뛴다. 그 뒤로는 처음 보는 기사를 최신 것부터 만들되, 한 번 확인에 {@code rss.max-per-check}편, 하루(24시간)에 {@code
 * rss.max-per-day}편까지만 만든다. 남는 기사는 건너뜀으로 남긴다. 본문을 못 찾는 등 만들지 못한 기사가 있으면 다음 기사로 넘어가되, 한 번 확인에 읽어 보는
 * 기사 수도 제한한다. 기사마다 기록을 먼저 남겨 같은 기사로 두 번 만들지 않는다.
 */
@Component
class FeedChecker {

  static final String START_FAILED = "만화를 시작하지 못했어요.";

  /** 비슷한 기사로 건너뛴 이유. */
  static String similarReason(String title) {
    return "최근에 만든 기사와 주제가 비슷해서 건너뛰었어요" + (title == null ? "." : ": " + title);
  }

  /** 칼럼·사설·인터뷰나 사진·영상 위주 기사라 건너뛴 이유 (004 FR-069). */
  static final String EXCLUDED_REASON = "칼럼·사설·인터뷰나 사진·영상 위주 기사라 건너뛰었어요.";

  /** 구독하기 전에 올라온 기사라 건너뛴 이유. */
  static final String BEFORE_SUBSCRIBE_REASON = "구독하기 전에 올라온 기사라 건너뛰었어요.";

  /** 한 번 확인에 만드는 편수를 넘어 건너뛴 이유. */
  static String perCheckReason(int max) {
    return "한 번에 만드는 편수(" + max + "편)를 넘어 건너뛰었어요.";
  }

  /** 하루에 만드는 편수를 다 써서 건너뛴 이유. */
  static String perDayReason(int max) {
    return "이 구독으로 하루에 만드는 편수(" + max + "편)를 다 써서 건너뛰었어요.";
  }

  /** 만들 자리 하나마다 읽어 볼 수 있는 기사 수. 본문을 못 찾는 기사가 이어져도 피드 전체를 읽지 않는다. */
  static final int TRIES_PER_SLOT = 3;

  private static final Logger log = LoggerFactory.getLogger(FeedChecker.class);

  private final RssFeedRepository feeds;
  private final RssFeedItemRepository items;
  private final ArticleReader reader;
  private final ConversationService conversations;
  private final SimilarArticles similar;
  private final Clock clock;
  private final int maxPerCheck;
  private final int maxPerDay;
  private final Set<Long> running = ConcurrentHashMap.newKeySet();
  private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

  FeedChecker(
      RssFeedRepository feeds,
      RssFeedItemRepository items,
      ArticleReader reader,
      ConversationService conversations,
      SimilarArticles similar,
      Clock clock,
      @Value("${rss.max-per-check:3}") int maxPerCheck,
      @Value("${rss.max-per-day:10}") int maxPerDay) {
    this.feeds = feeds;
    this.items = items;
    this.reader = reader;
    this.conversations = conversations;
    this.similar = similar;
    this.clock = clock;
    this.maxPerCheck = Math.max(1, maxPerCheck);
    this.maxPerDay = Math.max(1, maxPerDay);
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
    List<FeedParser.Entry> fresh = newestFirst(fresh(feedId, parsed.entries()));
    int limit;
    String overReason;
    if (!items.existsByFeedId(feedId)) {
      limit = 1;
      overReason = BEFORE_SUBSCRIBE_REASON;
    } else {
      long today =
          items.countByFeedIdAndStatusAndCreatedAtAfter(
              feedId, RssFeedItem.Status.MADE, clock.instant().minus(Duration.ofDays(1)));
      int left = (int) Math.max(0, maxPerDay - today);
      limit = Math.min(maxPerCheck, left);
      overReason = left <= maxPerCheck ? perDayReason(maxPerDay) : perCheckReason(maxPerCheck);
    }
    feeds.checked(feedId, clock.instant(), null);
    int made = 0;
    int tried = 0;
    for (FeedParser.Entry entry : fresh) {
      if (made >= limit || tried >= limit * TRIES_PER_SLOT) {
        skip(feedId, entry, overReason);
        continue;
      }
      tried++;
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

  /** 최신 것부터: 모두 발행 시각이 있으면 늦은 시각 순, 아니면 피드 순서 그대로(피드는 보통 최신순). */
  static List<FeedParser.Entry> newestFirst(List<FeedParser.Entry> entries) {
    List<FeedParser.Entry> out = new ArrayList<>(entries);
    if (out.stream().map(FeedParser.Entry::published).allMatch(Objects::nonNull)) {
      out.sort(Comparator.comparing(FeedParser.Entry::published).reversed());
    }
    return out;
  }

  /** 만들지 않고 건너뜀으로 남긴다. */
  private void skip(Long feedId, FeedParser.Entry entry, String reason) {
    RssFeedItem item = new RssFeedItem(feedId, entry, RssFeedItem.Status.SKIPPED);
    item.skipped(reason);
    try {
      items.saveAndFlush(item);
    } catch (DataIntegrityViolationException e) {
      log.debug("다른 확인이 먼저 남긴 기사: feed={} link={}", feedId, entry.link());
    }
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
    if (ExcludedArticle.matches(entry.title(), entry.link(), null)) {
      item.skipped(EXCLUDED_REASON);
      items.save(item);
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
      if (e.reason() == ArticleException.Reason.EXCLUDED) {
        item.skipped(EXCLUDED_REASON);
      } else {
        item.failed(e.userMessage());
      }
    } catch (RuntimeException e) {
      log.warn("RSS 기사로 대화를 시작하지 못했다: feed={} link={}", feed.getId(), entry.link(), e);
      item.failed(START_FAILED);
    }
    items.save(item);
    return item.getStatus() == RssFeedItem.Status.MADE;
  }
}
