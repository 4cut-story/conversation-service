package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedDialectTest {

  @Test
  @DisplayName("004 FR-059: 구독 사투리가 자동이면 기사마다 골라 절반쯤은 다섯 사투리를 고르게 섞는다")
  void autoMixes() {
    Map<String, Integer> counts = new HashMap<>();
    IntStream.range(0, 2000)
        .mapToObj(i -> FeedDialect.pick("AUTO", null, "https://news.example.com/a/" + i))
        .forEach(d -> counts.merge(String.valueOf(d), 1, Integer::sum));

    assertThat(counts)
        .containsOnlyKeys("GYEONGSANG", "JEOLLA", "CHUNGCHEONG", "GANGWON", "SEOUL", "null");
    assertThat(counts.get("null")).isBetween(800, 1200);
    counts.forEach(
        (d, n) -> {
          if (!"null".equals(d)) {
            assertThat(n).as(d).isBetween(120, 280);
          }
        });
  }

  @Test
  @DisplayName("004 FR-059: 같은 기사는 늘 같은 사투리, 고른 사투리·없음은 그대로, 흑백 신문 구독은 자동이어도 없음")
  void fixedChoices() {
    String link = "https://news.example.com/a/42";
    assertThat(FeedDialect.pick("AUTO", "DAILY", link))
        .isEqualTo(FeedDialect.pick("AUTO", null, link));
    assertThat(FeedDialect.pick("JEOLLA", "NEWSPAPER", link)).isEqualTo("JEOLLA");
    assertThat(FeedDialect.pick(null, null, link)).isNull();
    assertThat(FeedDialect.pick("AUTO", "NEWSPAPER", link)).isNull();
    assertThat(FeedDialect.pick("AUTO", null, null)).isNull();
  }

  @Test
  @DisplayName("004 FR-050: 구독 사투리를 보내지 않으면 자동, 없음은 null로 저장한다")
  void stored() {
    assertThat(RssFeedApi.dialect(null)).isEqualTo("AUTO");
    assertThat(RssFeedApi.dialect("AUTO")).isEqualTo("AUTO");
    assertThat(RssFeedApi.dialect("NONE")).isNull();
    assertThat(RssFeedApi.dialect("GANGWON")).isEqualTo("GANGWON");
  }
}
