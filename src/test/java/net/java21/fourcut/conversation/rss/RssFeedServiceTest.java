package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.article.ArticleException;
import net.java21.fourcut.conversation.article.ArticleReader;
import net.java21.fourcut.conversation.article.FeedDocument;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class RssFeedServiceTest {

  private static final String URL = "https://news.example.com/rss";

  private final RssFeedRepository feeds = mock(RssFeedRepository.class);
  private final RssFeedItemRepository items = mock(RssFeedItemRepository.class);
  private final ArticleReader reader = mock(ArticleReader.class);
  private final FeedChecker checker = mock(FeedChecker.class);
  private final RssFeedService service = new RssFeedService(feeds, items, reader, checker);

  private void serveFeed() {
    when(reader.fetchFeed(URL))
        .thenReturn(
            new FeedDocument(
                URL,
                "<rss><channel><title>예시신문</title><link>https://news.example.com/</link></channel></rss>"
                    .getBytes(StandardCharsets.UTF_8)));
  }

  private static RssFeed stored(String owner) {
    RssFeed feed = new RssFeed(owner, URL, "예시신문", null);
    ReflectionTestUtils.setField(feed, "id", 3L);
    return feed;
  }

  private static ErrorCode code(Runnable call) {
    try {
      call.run();
      throw new AssertionError("오류가 나지 않았다");
    } catch (ApiException e) {
      return e.code();
    }
  }

  @Test
  @DisplayName("004 FR-050·FR-052: 피드를 읽어 제목을 채워 등록하고 바로 첫 확인을 시작한다. 공개 방식 기본은 확인 후 공개")
  void create() {
    serveFeed();
    when(feeds.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              RssFeed f = inv.getArgument(0);
              ReflectionTestUtils.setField(f, "id", 3L);
              return f;
            });

    RssFeedApi.View view =
        service.create("u", new RssFeedApi.CreateRequest(" " + URL + " ", null, "AUTO", "NONE"));

    assertThat(view.title()).isEqualTo("예시신문");
    assertThat(view.url()).isEqualTo(URL);
    assertThat(view.siteUrl()).isEqualTo("https://news.example.com/");
    assertThat(view.autoPublish()).isFalse();
    assertThat(view.enabled()).isTrue();
    assertThat(view.concept()).isEqualTo("AUTO");
    assertThat(view.dialect()).isEqualTo("NONE");
    verify(checker).checkLater(3L);
  }

  @Test
  @DisplayName("004 FR-051·FR-053: 이미 등록한 주소는 409, 읽지 못하는 피드는 422로 이유를 알린다")
  void createRejects() {
    when(feeds.existsByOwnerIdAndUrl("u", URL)).thenReturn(true);
    assertThat(code(() -> service.create("u", new RssFeedApi.CreateRequest(URL, true, null, null))))
        .isEqualTo(ErrorCode.RSS_DUPLICATE);

    when(feeds.existsByOwnerIdAndUrl("u", URL)).thenReturn(false);
    when(reader.fetchFeed(URL))
        .thenThrow(new ArticleException(ArticleException.Reason.BLOCKED_URL, "private"));
    assertThatThrownBy(
            () -> service.create("u", new RssFeedApi.CreateRequest(URL, true, null, null)))
        .isInstanceOf(ApiException.class)
        .hasMessage(ArticleException.Reason.BLOCKED_URL.message())
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(ErrorCode.RSS_UNREADABLE);
    verify(checker, never()).checkLater(any());
  }

  @Test
  @DisplayName("004 FR-053: 동시에 같은 주소를 등록하면(유일 키 충돌) 409")
  void createRace() {
    serveFeed();
    doThrow(new DataIntegrityViolationException("uk")).when(feeds).saveAndFlush(any());
    assertThat(code(() -> service.create("u", new RssFeedApi.CreateRequest(URL, true, null, null))))
        .isEqualTo(ErrorCode.RSS_DUPLICATE);
  }

  @Test
  @DisplayName("004 FR-054·FR-065·FR-066: 설정 바꾸기·지금 확인은 주인만, 남의 구독은 404. 목록에 만든 수와 최근 기사가 나온다")
  void manage() {
    RssFeed feed = stored("u");
    when(feeds.findByPublicId("f")).thenReturn(Optional.of(feed));
    when(items.countByFeedIdAndStatus(3L, RssFeedItem.Status.MADE)).thenReturn(4L);
    RssFeedItem item =
        new RssFeedItem(
            3L,
            new FeedParser.Entry("k", "https://news.example.com/a/1", "떡집", null),
            RssFeedItem.Status.MADE);
    item.made("conv");
    when(items.findTop5ByFeedIdOrderByCreatedAtDescIdDesc(3L)).thenReturn(List.of(item));

    RssFeedApi.View view =
        service.update("u", "f", new RssFeedApi.UpdateRequest(true, false, "JOSEON", "JEOLLA"));
    assertThat(view.autoPublish()).isTrue();
    assertThat(view.enabled()).isFalse();
    assertThat(view.concept()).isEqualTo("JOSEON");
    assertThat(view.dialect()).isEqualTo("JEOLLA");
    assertThat(view.made()).isEqualTo(4);
    assertThat(view.recent())
        .singleElement()
        .satisfies(
            r -> {
              assertThat(r.title()).isEqualTo("떡집");
              assertThat(r.conversationId()).isEqualTo("conv");
              assertThat(r.status()).isEqualTo(RssFeedItem.Status.MADE);
            });

    service.check("u", "f");
    verify(checker).checkLater(3L);

    when(feeds.findByOwnerIdOrderByCreatedAtDescIdDesc("u")).thenReturn(List.of(feed));
    assertThat(service.list("u").items()).hasSize(1);

    assertThat(
            code(
                () ->
                    service.update(
                        "other", "f", new RssFeedApi.UpdateRequest(true, true, null, null))))
        .isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(code(() -> service.check("other", "f"))).isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  @DisplayName("004 FR-066: 지우기는 주인 것만 지우고, 없거나 남의 것이면 조용히 끝난다")
  void delete() {
    RssFeed feed = stored("u");
    when(feeds.findByPublicId("f")).thenReturn(Optional.of(feed));
    service.delete("other", "f");
    service.delete("u", "missing");
    verify(feeds, never()).delete(any());
    service.delete("u", "f");
    verify(feeds).delete(feed);
  }
}
