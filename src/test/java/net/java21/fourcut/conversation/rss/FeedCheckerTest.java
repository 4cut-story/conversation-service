package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.article.ArticleException;
import net.java21.fourcut.conversation.article.ArticleReader;
import net.java21.fourcut.conversation.article.FeedDocument;
import net.java21.fourcut.conversation.conversations.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class FeedCheckerTest {

  private static final String URL = "https://news.example.com/rss";
  private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

  private final RssFeedRepository feeds = mock(RssFeedRepository.class);
  private final RssFeedItemRepository items = mock(RssFeedItemRepository.class);
  private final ArticleReader reader = mock(ArticleReader.class);
  private final ConversationService conversations = mock(ConversationService.class);
  private final SimilarArticles similar = mock(SimilarArticles.class);
  private final FeedChecker checker =
      new FeedChecker(
          feeds, items, reader, conversations, similar, Clock.fixed(NOW, ZoneOffset.UTC), 3, 10);
  private final List<RssFeedItem> saved = new ArrayList<>();
  private final RssFeed feed = new RssFeed("owner", URL, "예시신문", null);

  @BeforeEach
  void setUp() {
    org.springframework.test.util.ReflectionTestUtils.setField(feed, "id", 7L);
    feed.configure(true, true, "WATERCOLOR", null);
    when(feeds.findById(7L)).thenReturn(Optional.of(feed));
    when(items.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              RssFeedItem item = inv.getArgument(0);
              saved.add(item);
              return item;
            });
    when(reader.read(anyString()))
        .thenAnswer(inv -> new Article(inv.getArgument(0), "제목", "예시신문", "ko", "본문"));
    when(conversations.createFromFeed(any(), anyLong(), anyBoolean(), any(), any(), any()))
        .thenAnswer(inv -> "conv-" + ((Article) inv.getArgument(5)).url().replaceAll("\\D", ""));
  }

  private void serve(String... items) {
    StringBuilder xml = new StringBuilder("<rss><channel><title>예시신문</title>");
    for (String item : items) {
      xml.append(item);
    }
    xml.append("</channel></rss>");
    when(reader.fetchFeed(URL))
        .thenReturn(new FeedDocument(URL, xml.toString().getBytes(StandardCharsets.UTF_8)));
  }

  private static String item(int n, String date) {
    return "<item><link>https://news.example.com/a/"
        + n
        + "</link>"
        + (date == null ? "" : "<pubDate>" + date + "</pubDate>")
        + "</item>";
  }

  private List<RssFeedItem.Status> statuses() {
    return saved.stream().map(RssFeedItem::getStatus).toList();
  }

  @Test
  @DisplayName("004 FR-056: 처음 확인하는 피드는 가장 최근 기사 1편만 만들고 나머지는 건너뛴다")
  void firstCheck() {
    serve(
        item(1, "Mon, 09 Nov 2026 01:00:00 GMT"),
        item(3, "Tue, 10 Nov 2026 01:00:00 GMT"),
        item(2, "Mon, 09 Nov 2026 12:00:00 GMT"));
    when(items.existsByFeedId(7L)).thenReturn(false);

    assertThat(checker.check(7L)).isEqualTo(1);

    verify(conversations)
        .createFromFeed(eq("owner"), anyLong(), eq(true), eq("WATERCOLOR"), isNull(), any());
    verify(reader).read("https://news.example.com/a/3");
    assertThat(saved)
        .extracting(RssFeedItem::getLink, RssFeedItem::getStatus)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/1", RssFeedItem.Status.SKIPPED),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/2", RssFeedItem.Status.SKIPPED),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/3", RssFeedItem.Status.MADE));
    assertThat(saved.stream().filter(i -> i.getStatus() == RssFeedItem.Status.MADE))
        .singleElement()
        .extracting(RssFeedItem::getConversationId)
        .isEqualTo("conv-3");
    assertThat(saved.stream().filter(i -> i.getStatus() == RssFeedItem.Status.SKIPPED))
        .extracting(RssFeedItem::getReason)
        .containsOnly(FeedChecker.BEFORE_SUBSCRIBE_REASON);
    verify(feeds).checked(7L, NOW, null);
  }

  @Test
  @DisplayName("004 FR-057·FR-058: 확인한 적 있는 피드는 처음 보는 기사를 최신 것부터 만들고, 본 기사는 다시 만들지 않는다")
  void laterChecks() {
    serve(item(5, null), item(4, null), item(3, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    when(items.seenKeys(eq(7L), any())).thenReturn(List.of("https://news.example.com/a/3"));

    assertThat(checker.check(7L)).isEqualTo(2);

    assertThat(saved)
        .extracting(RssFeedItem::getLink)
        .containsExactly("https://news.example.com/a/5", "https://news.example.com/a/4");
    assertThat(statuses()).containsOnly(RssFeedItem.Status.MADE);
  }

  @Test
  @DisplayName("한 번 확인에 최신 3편까지만 만들고, 나머지는 이유를 남겨 건너뛴다")
  void perCheckLimit() {
    serve(item(6, null), item(5, null), item(4, null), item(3, null), item(2, null));
    when(items.existsByFeedId(7L)).thenReturn(true);

    assertThat(checker.check(7L)).isEqualTo(3);

    assertThat(saved)
        .extracting(RssFeedItem::getLink, RssFeedItem::getStatus, RssFeedItem::getReason)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/6", RssFeedItem.Status.MADE, null),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/5", RssFeedItem.Status.MADE, null),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/4", RssFeedItem.Status.MADE, null),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/3",
                RssFeedItem.Status.SKIPPED,
                FeedChecker.perCheckReason(3)),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/2",
                RssFeedItem.Status.SKIPPED,
                FeedChecker.perCheckReason(3)));
  }

  @Test
  @DisplayName("004 FR-057: 하루(한국 시각 0시부터)에 만드는 편수를 다 쓰면 남은 만큼만 만들고 나머지는 건너뛴다")
  void perDayLimit() {
    serve(item(3, null), item(2, null), item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    when(items.countByFeedIdAndStatusAndCreatedAtAfter(
            7L, RssFeedItem.Status.MADE, Instant.parse("2026-11-09T15:00:00Z")))
        .thenReturn(9L);

    assertThat(checker.check(7L)).isEqualTo(1);

    assertThat(statuses())
        .containsExactly(
            RssFeedItem.Status.MADE, RssFeedItem.Status.SKIPPED, RssFeedItem.Status.SKIPPED);
    assertThat(saved.get(1).getReason()).isEqualTo(FeedChecker.perDayReason(10));
  }

  @Test
  @DisplayName("본문을 못 찾은 기사가 이어지면 다음 기사를 읽어 보되, 한 번 확인에 읽는 기사 수는 제한한다")
  void triesLimit() {
    String[] entries = new String[12];
    for (int i = 0; i < 12; i++) {
      entries[i] = item(100 - i, null);
    }
    serve(entries);
    when(items.existsByFeedId(7L)).thenReturn(false);
    when(reader.read(anyString()))
        .thenThrow(new ArticleException(ArticleException.Reason.NO_BODY, "짧다"));

    assertThat(checker.check(7L)).isZero();

    verify(reader, org.mockito.Mockito.times(FeedChecker.TRIES_PER_SLOT)).read(anyString());
    assertThat(statuses())
        .containsOnly(RssFeedItem.Status.FAILED, RssFeedItem.Status.SKIPPED)
        .filteredOn(st -> st == RssFeedItem.Status.FAILED)
        .hasSize(FeedChecker.TRIES_PER_SLOT);
  }

  @Test
  @DisplayName("004 FR-060: 읽지 못한 기사는 실패로 남기고 다음 기사를 계속 만든다")
  void articleFailure() {
    serve(item(2, null), item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    when(reader.read("https://news.example.com/a/1"))
        .thenThrow(new ArticleException(ArticleException.Reason.NO_BODY, "짧다"));

    assertThat(checker.check(7L)).isEqualTo(1);

    assertThat(saved)
        .extracting(RssFeedItem::getStatus, RssFeedItem::getReason)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(RssFeedItem.Status.MADE, null),
            org.assertj.core.groups.Tuple.tuple(
                RssFeedItem.Status.FAILED, ArticleException.Reason.NO_BODY.message()));
  }

  @Test
  @DisplayName("004 FR-069: 칼럼·사설·인터뷰·사진·영상 기사는 읽지 않고 건너뛰고, 읽은 뒤 알게 된 것도 건너뜀으로 남긴다")
  void skipsOpinion() {
    serve(
        "<item><link>https://news.example.com/a/3</link><title>[사설] 물가 대책</title></item>",
        item(2, null),
        item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    when(reader.read("https://news.example.com/a/2"))
        .thenThrow(new ArticleException(ArticleException.Reason.EXCLUDED, "분류"));

    assertThat(checker.check(7L)).isEqualTo(1);

    verify(reader, never()).read("https://news.example.com/a/3");
    assertThat(saved)
        .extracting(RssFeedItem::getLink, RssFeedItem::getStatus, RssFeedItem::getReason)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/3",
                RssFeedItem.Status.SKIPPED,
                FeedChecker.EXCLUDED_REASON),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/2",
                RssFeedItem.Status.SKIPPED,
                FeedChecker.EXCLUDED_REASON),
            org.assertj.core.groups.Tuple.tuple(
                "https://news.example.com/a/1", RssFeedItem.Status.MADE, null));
  }

  @Test
  @DisplayName("004 FR-068: 최근에 만든 기사와 주제가 비슷하면 만들지 않고 건너뛴 이유를 남긴다")
  void skipsSimilar() {
    serve(item(2, null), item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    float[] vector = {1f, 0f};
    when(similar.embed(any())).thenReturn(vector);
    RssFeedItem earlier =
        new RssFeedItem(
            7L,
            new FeedParser.Entry("k", "https://other.example.com/x", "떡집 마지막 날", null),
            RssFeedItem.Status.MADE);
    when(similar.find(eq("owner"), any(), any(), eq(vector)))
        .thenReturn(Optional.of(earlier))
        .thenReturn(Optional.empty());

    assertThat(checker.check(7L)).isEqualTo(1);

    assertThat(saved)
        .extracting(RssFeedItem::getStatus)
        .containsExactly(RssFeedItem.Status.SIMILAR, RssFeedItem.Status.MADE);
    assertThat(saved.get(0).getReason()).contains("떡집 마지막 날");
    assertThat(saved.get(0).vector()).containsExactly(1f, 0f);
    verify(conversations, org.mockito.Mockito.times(1))
        .createFromFeed(any(), anyLong(), anyBoolean(), any(), any(), any());
  }

  @Test
  @DisplayName("004 FR-060: 대화를 시작하다 오류가 나면 실패로 남긴다")
  void startFailure() {
    serve(item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    org.mockito.Mockito.doThrow(new IllegalStateException("db"))
        .when(conversations)
        .createFromFeed(any(), anyLong(), anyBoolean(), any(), any(), any());

    assertThat(checker.check(7L)).isZero();
    assertThat(saved)
        .singleElement()
        .extracting(RssFeedItem::getReason)
        .isEqualTo(FeedChecker.START_FAILED);
  }

  @Test
  @DisplayName("004 FR-058: 다른 확인이 먼저 기록한 기사(유일 키 충돌)는 만들지 않는다")
  void duplicate() {
    serve(item(1, null));
    when(items.existsByFeedId(7L)).thenReturn(true);
    org.mockito.Mockito.doThrow(new DataIntegrityViolationException("uk"))
        .when(items)
        .saveAndFlush(any());

    assertThat(checker.check(7L)).isZero();
    verify(reader, never()).read(anyString());
  }

  @Test
  @DisplayName("004 FR-060: 피드를 읽지 못하면 마지막 오류에 이유를 남기고, 다음 확인에 다시 한다")
  void feedFailure() {
    when(reader.fetchFeed(URL))
        .thenThrow(new ArticleException(ArticleException.Reason.UNREACHABLE, "timeout"));

    assertThat(checker.check(7L)).isZero();
    verify(feeds).checked(7L, NOW, ArticleException.Reason.UNREACHABLE.message());
  }

  @Test
  @DisplayName("004 FR-055: 켜진 피드를 모두 확인하고, 지운 피드나 예상 못 한 오류는 건너뛴다")
  void checkAll() {
    serve(item(1, null));
    when(feeds.enabledIds()).thenReturn(List.of(7L, 8L, 9L));
    when(feeds.findById(8L)).thenReturn(Optional.empty());
    when(feeds.findById(9L)).thenThrow(new IllegalStateException("db"));
    when(items.existsByFeedId(7L)).thenReturn(true);

    assertThat(checker.checkAll()).isEqualTo(1);
  }

  @Test
  @DisplayName("004 FR-055: 지금 확인은 뒤에서 돈다")
  void checkLater() {
    serve();
    checker.checkLater(7L);
    verify(feeds, timeout(5000)).checked(7L, NOW, null);
  }

  @Test
  @DisplayName("004 FR-056·FR-057: 발행 시각이 없으면 피드 순서(최신순)로 최근·오래된 순서를 정한다")
  void ordering() {
    FeedParser.Entry a = new FeedParser.Entry("a", "https://x/a", null, null);
    FeedParser.Entry b = new FeedParser.Entry("b", "https://x/b", null, null);
    assertThat(FeedChecker.latest(List.of(a, b))).isEqualTo(a);
    assertThat(FeedChecker.latest(List.of())).isNull();
    assertThat(FeedChecker.newestFirst(List.of(a, b))).containsExactly(a, b);
    FeedParser.Entry c =
        new FeedParser.Entry("c", "https://x/c", null, Instant.parse("2026-11-09T00:00:00Z"));
    FeedParser.Entry d =
        new FeedParser.Entry("d", "https://x/d", null, Instant.parse("2026-11-10T00:00:00Z"));
    assertThat(FeedChecker.newestFirst(List.of(c, d))).containsExactly(d, c);
  }
}
