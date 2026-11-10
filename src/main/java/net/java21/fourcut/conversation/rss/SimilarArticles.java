package net.java21.fourcut.conversation.rss;

import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.java21.fourcut.conversation.article.Article;
import net.java21.fourcut.conversation.character.TextEmbedder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 최근에 만든 기사와 주제가 비슷한지 (004 FR-068). 여러 언론사가 같은 일을 다룬 기사, 같은 피드에 다시 올라온 기사를 거른다. 기사 제목과 본문 앞부분의 설명
 * 임베딩(BGE-M3, 길이 1)끼리 코사인 유사도를 재고, 임베딩이 없으면 제목 두 글자 묶음(바이그램) 겹침으로 잰다. 그 사람의 모든 구독에서 최근(기본 3일) 만든
 * 기사와 비교한다.
 */
@Component
class SimilarArticles {

  /** 임베딩에 넣을 본문 앞부분 길이. */
  static final int LEAD_CHARS = 600;

  private final TextEmbedder embedder;
  private final RssFeedItemRepository items;
  private final Clock clock;
  private final Duration within;
  private final double threshold;
  private final double titleThreshold;

  SimilarArticles(
      TextEmbedder embedder,
      RssFeedItemRepository items,
      Clock clock,
      @Value("${rss.similar-within:3d}") Duration within,
      @Value("${rss.similar-threshold:0.82}") double threshold,
      @Value("${rss.similar-title-threshold:0.6}") double titleThreshold) {
    this.embedder = embedder;
    this.items = items;
    this.clock = clock;
    this.within = within;
    this.threshold = threshold;
    this.titleThreshold = titleThreshold;
  }

  /** 기사 설명 임베딩. 임베딩 서버를 못 쓰면 null. */
  float[] embed(Article article) {
    String text = article.text() == null ? "" : article.text();
    String lead = text.length() <= LEAD_CHARS ? text : text.substring(0, LEAD_CHARS);
    return embedder.embed((article.title() + "\n" + lead).strip()).orElse(null);
  }

  /**
   * 최근에 만든 비슷한 기사.
   *
   * @param self 지금 만드는 기사 기록 (비교에서 뺀다)
   * @param vector 지금 기사의 임베딩. 없으면 제목으로만 비교한다
   */
  Optional<RssFeedItem> find(String owner, RssFeedItem self, String title, float[] vector) {
    if (within.isZero() || within.isNegative()) {
      return Optional.empty();
    }
    for (RssFeedItem made : items.madeSince(owner, clock.instant().minus(within))) {
      if (Objects.equals(made.getId(), self.getId())) {
        continue;
      }
      float[] other = made.vector();
      boolean close =
          vector != null && vector.length > 0 && other.length == vector.length
              ? cosine(vector, other) >= threshold
              : titleOverlap(title, made.getTitle()) >= titleThreshold;
      if (close) {
        return Optional.of(made);
      }
    }
    return Optional.empty();
  }

  /** 길이 1 벡터의 코사인 유사도 (내적). */
  static double cosine(float[] a, float[] b) {
    double dot = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
    }
    return dot;
  }

  /** 제목 두 글자 묶음의 자카드 유사도 (공백·문장 부호 무시). 하나라도 없으면 0. */
  static double titleOverlap(String a, String b) {
    Set<String> x = bigrams(a);
    Set<String> y = bigrams(b);
    if (x.isEmpty() || y.isEmpty()) {
      return 0;
    }
    Set<String> both = new HashSet<>(x);
    both.retainAll(y);
    Set<String> any = new HashSet<>(x);
    any.addAll(y);
    return (double) both.size() / any.size();
  }

  private static Set<String> bigrams(String text) {
    Set<String> out = new HashSet<>();
    if (text == null) {
      return out;
    }
    String s = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    for (int i = 0; i + 2 <= s.length(); i++) {
      out.add(s.substring(i, i + 2));
    }
    return out;
  }
}
