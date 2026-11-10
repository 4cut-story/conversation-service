package net.java21.fourcut.conversation.rss;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface RssFeedItemRepository extends JpaRepository<RssFeedItem, Long> {

  boolean existsByFeedId(Long feedId);

  @Query("select i.itemKey from RssFeedItem i where i.feedId = ?1 and i.itemKey in ?2")
  List<String> seenKeys(Long feedId, Collection<String> keys);

  /** 그 사람이 그 시각 뒤에 만든 기사 (모든 구독). 주제가 비슷한지 비교할 때 쓴다. */
  @Query(
      "select i from RssFeedItem i, RssFeed f where f.id = i.feedId and f.ownerId = ?1"
          + " and i.status = net.java21.fourcut.conversation.rss.RssFeedItem.Status.MADE"
          + " and i.createdAt >= ?2 order by i.createdAt desc")
  List<RssFeedItem> madeSince(String ownerId, Instant since);

  long countByFeedIdAndStatus(Long feedId, RssFeedItem.Status status);

  /** 그 피드에서 그 시각 뒤에 그 상태로 남긴 기사 수. 하루에 만드는 편수를 셀 때 쓴다. */
  long countByFeedIdAndStatusAndCreatedAtAfter(
      Long feedId, RssFeedItem.Status status, Instant after);

  List<RssFeedItem> findTop5ByFeedIdOrderByCreatedAtDescIdDesc(Long feedId);
}
