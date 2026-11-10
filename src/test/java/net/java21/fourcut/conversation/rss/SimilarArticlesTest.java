package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.character.TextEmbedder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SimilarArticlesTest {

  private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");

  private final TextEmbedder embedder = mock(TextEmbedder.class);
  private final RssFeedItemRepository items = mock(RssFeedItemRepository.class);

  private SimilarArticles similar(Duration within) {
    return new SimilarArticles(
        embedder, items, Clock.fixed(NOW, ZoneOffset.UTC), within, 0.82, 0.6);
  }

  private static RssFeedItem made(long id, String title, float[] vector) {
    RssFeedItem item =
        new RssFeedItem(
            1L,
            new FeedParser.Entry("k" + id, "https://n.example.com/" + id, title, null),
            RssFeedItem.Status.MADE);
    item.embedded(vector);
    ReflectionTestUtils.setField(item, "id", id);
    return item;
  }

  private static float[] unit(double angle) {
    return new float[] {(float) Math.cos(angle), (float) Math.sin(angle)};
  }

  @Test
  @DisplayName("004 FR-068: 최근 3일 안에 만든 기사와 임베딩이 가까우면 비슷하다고 보고, 멀면 만든다. 자기 자신은 빼고 본다")
  void byEmbedding() {
    RssFeedItem self = made(9, "지금 기사", unit(0));
    RssFeedItem near = made(1, "다른 제목", unit(0.3)); // cos 0.955
    RssFeedItem far = made(2, "또 다른 제목", unit(1.2)); // cos 0.36
    when(items.madeSince("u", NOW.minus(Duration.ofDays(3)))).thenReturn(List.of(self, far));
    assertThat(similar(Duration.ofDays(3)).find("u", self, "지금 기사", unit(0))).isEmpty();

    when(items.madeSince("u", NOW.minus(Duration.ofDays(3)))).thenReturn(List.of(self, far, near));
    assertThat(similar(Duration.ofDays(3)).find("u", self, "지금 기사", unit(0))).contains(near);
  }

  @Test
  @DisplayName("004 FR-068: 임베딩이 없으면 제목이 많이 겹칠 때 비슷하다고 본다")
  void byTitle() {
    RssFeedItem self = made(9, null, null);
    RssFeedItem same = made(1, "[속보] 30년 떡집, 마지막 영업", null);
    when(items.madeSince(any(), any())).thenReturn(List.of(same));
    assertThat(similar(Duration.ofDays(3)).find("u", self, "30년 떡집 마지막 영업", null)).contains(same);
    assertThat(similar(Duration.ofDays(3)).find("u", self, "프로야구 개막전 매진", null)).isEmpty();
    assertThat(SimilarArticles.titleOverlap(null, "떡집")).isZero();
  }

  @Test
  @DisplayName("004 FR-068: 기간을 0으로 두면 비교하지 않는다")
  void disabled() {
    assertThat(similar(Duration.ZERO).find("u", made(9, null, null), "t", null)).isEmpty();
    verify(items, never()).madeSince(any(), any());
  }

  @Test
  @DisplayName("004 FR-068: 기사 제목과 본문 앞부분으로 임베딩하고, 임베딩 서버를 못 쓰면 null")
  void embed() {
    String text = "가".repeat(1000);
    when(embedder.embed(anyString())).thenReturn(Optional.of(new float[] {1f}));
    assertThat(similar(Duration.ofDays(3)).embed(new Article("u", "제목", "s", "ko", text)))
        .containsExactly(1f);
    verify(embedder).embed("제목\n" + "가".repeat(SimilarArticles.LEAD_CHARS));
    when(embedder.embed(anyString())).thenReturn(Optional.empty());
    assertThat(similar(Duration.ofDays(3)).embed(new Article("u", "제목", "s", "ko", null))).isNull();
  }
}
