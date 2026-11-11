package net.java21.fourcut.conversation.rss;

import java.util.List;
import net.java21.fourcut.conversation.article.ArticleException;
import net.java21.fourcut.conversation.article.ArticleReader;
import net.java21.fourcut.conversation.article.FeedDocument;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 구독 등록·목록·설정·삭제·지금 확인 (004 FR-050~054, FR-065, FR-066). 남의 구독은 없는 것처럼 404. */
@Service
class RssFeedService {

  private final RssFeedRepository feeds;
  private final RssFeedItemRepository items;
  private final ArticleReader reader;
  private final FeedChecker checker;

  RssFeedService(
      RssFeedRepository feeds,
      RssFeedItemRepository items,
      ArticleReader reader,
      FeedChecker checker) {
    this.feeds = feeds;
    this.items = items;
    this.reader = reader;
    this.checker = checker;
  }

  @Transactional(readOnly = true)
  public RssFeedApi.ListView list(String owner) {
    return new RssFeedApi.ListView(
        feeds.findByOwnerIdOrderByCreatedAtDescIdDesc(owner).stream().map(this::view).toList());
  }

  /** 피드를 읽어 제목을 채워 등록하고, 바로 첫 확인을 시작한다. 피드를 읽는 동안(최대 십여 초)은 트랜잭션 밖이다. */
  public RssFeedApi.View create(String owner, RssFeedApi.CreateRequest request) {
    String url = request.url().strip();
    if (feeds.existsByOwnerIdAndUrl(owner, url)) {
      throw new ApiException(ErrorCode.RSS_DUPLICATE);
    }
    FeedParser.Feed parsed;
    try {
      FeedDocument doc = reader.fetchFeed(url);
      parsed = FeedParser.parse(doc.body(), doc.url());
    } catch (ArticleException e) {
      throw new ApiException(ErrorCode.RSS_UNREADABLE, e.userMessage(), e);
    }
    RssFeed feed = new RssFeed(owner, url, parsed.title(), parsed.siteUrl());
    feed.configure(
        Boolean.TRUE.equals(request.autoPublish()),
        true,
        RssFeedApi.concept(request.concept()),
        RssFeedApi.dialect(request.dialect()));
    RssFeed saved;
    try {
      saved = feeds.saveAndFlush(feed);
    } catch (DataIntegrityViolationException e) {
      throw new ApiException(ErrorCode.RSS_DUPLICATE, ErrorCode.RSS_DUPLICATE.message(), e);
    }
    checker.checkLater(saved.getId());
    return view(saved);
  }

  @Transactional
  public RssFeedApi.View update(String owner, String id, RssFeedApi.UpdateRequest request) {
    RssFeed feed = owned(owner, id);
    boolean turnedOn = !feed.isEnabled() && request.enabled();
    feed.configure(
        request.autoPublish(),
        request.enabled(),
        RssFeedApi.concept(request.concept()),
        RssFeedApi.dialect(request.dialect()));
    if (turnedOn) {
      checkAfterCommit(feed.getId());
    }
    return view(feed);
  }

  /** 꺼 둔 피드를 켜면 다음 정기 확인(최대 30분)을 기다리지 않고 바로 확인한다. 바뀐 설정이 저장된 뒤에 확인한다. */
  private void checkAfterCommit(Long feedId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      checker.checkLater(feedId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            checker.checkLater(feedId);
          }
        });
  }

  /** 구독을 지운다. 기사 기록은 DB가 함께 지우고(ON DELETE CASCADE), 만든 만화는 남는다. 없어도 조용히 끝난다(멱등). */
  @Transactional
  public void delete(String owner, String id) {
    feeds.findByPublicId(id).filter(f -> f.getOwnerId().equals(owner)).ifPresent(feeds::delete);
  }

  /** 지금 확인. 뒤에서 확인하고 바로 돌아온다. */
  @Transactional(readOnly = true)
  public RssFeedApi.View check(String owner, String id) {
    RssFeed feed = owned(owner, id);
    checker.checkLater(feed.getId());
    return view(feed);
  }

  private RssFeed owned(String owner, String id) {
    return feeds
        .findByPublicId(id)
        .filter(f -> f.getOwnerId().equals(owner))
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
  }

  private RssFeedApi.View view(RssFeed f) {
    List<RssFeedApi.ItemView> recent =
        items.findTop5ByFeedIdOrderByCreatedAtDescIdDesc(f.getId()).stream()
            .map(
                i ->
                    new RssFeedApi.ItemView(
                        i.getTitle(),
                        i.getLink(),
                        i.getStatus(),
                        i.getReason(),
                        i.getConversationId(),
                        i.getCreatedAt()))
            .toList();
    return new RssFeedApi.View(
        f.getPublicId(),
        f.getUrl(),
        f.getTitle(),
        f.getSiteUrl(),
        f.isAutoPublish(),
        f.isEnabled(),
        f.getConcept() == null ? "AUTO" : f.getConcept(),
        f.getDialect() == null ? "NONE" : f.getDialect(),
        f.getLastCheckedAt(),
        f.getLastError(),
        items.countByFeedIdAndStatus(f.getId(), RssFeedItem.Status.MADE),
        f.getCreatedAt(),
        recent);
  }
}
