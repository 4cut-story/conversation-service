package net.java21.fourcut.conversation.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArticleExtractorTest {

  static final String PARAGRAPH = "동네 시장 한가운데에 30년 된 떡집이 문을 닫는다는 소식이 전해지자 단골손님들이 아침부터 줄을 섰다.";

  static String articleHtml() {
    return """
        <html lang="ko"><head>
        <title>떡집 마지막 날 | 예시신문</title>
        <meta property="og:title" content="30년 떡집의 마지막 날">
        <meta property="og:site_name" content="예시신문">
        <script>var ad = 1;</script></head>
        <body>
        <nav class="gnb"><a href="/">홈</a><a href="/politics">정치</a></nav>
        <div class="ad-banner">지금 가입하면 할인!</div>
        <article>
          <h1>30년 떡집의 마지막 날</h1>
          <div itemprop="articleBody">
            <p>%s</p>
            <p>주인 할머니는 "그동안 고마웠다"며 남은 떡을 모두 나눠 주었고, 손님들은 꽃다발과 손편지로 답했다.</p>
            <p>가게 자리에는 손주가 카페를 열 예정이라 단골들은 다시 오겠다고 약속했다.</p>
            <p>이웃 상인들은 떡집 앞에 작은 현수막을 걸어 30년 동안 시장을 지켜 준 할머니에게 고마운 마음을 전했고, 할머니는 눈시울을 붉혔다.</p>
            <p>할머니는 마지막 손님을 배웅한 뒤 가게 문에 "그동안 정말 감사했습니다"라는 쪽지를 붙이고 불을 껐다.</p>
            <p>홍길동 기자 hong@example.com</p>
            <p>ⓒ 예시신문 무단 전재 및 재배포 금지</p>
          </div>
          <div class="related-news"><p>관련 기사: 다른 이야기</p></div>
        </article>
        <footer>회사 소개</footer>
        </body></html>
        """
        .formatted(PARAGRAPH);
  }

  @Test
  @DisplayName("기사로 만들기: 본문 자리에서 문단만 고르고 메뉴·광고·기자·저작권 줄은 뺀다")
  void extractsBody() {
    Article article =
        ArticleExtractor.extract(
            "https://news.example.com/a/1",
            "www.news.example.com",
            articleHtml().getBytes(StandardCharsets.UTF_8));
    assertThat(article.title()).isEqualTo("30년 떡집의 마지막 날");
    assertThat(article.site()).isEqualTo("예시신문");
    assertThat(article.language()).isEqualTo("ko");
    assertThat(article.text())
        .contains(PARAGRAPH)
        .contains("손주가 카페를")
        .doesNotContain("hong@example.com")
        .doesNotContain("무단 전재")
        .doesNotContain("정치")
        .doesNotContain("할인")
        .doesNotContain("관련 기사");
    assertThat(article.text().split("\n")).hasSize(5);
  }

  @Test
  @DisplayName("기사로 만들기: 알려진 본문 자리가 없으면 긴 문단이 모인 블록을 본문으로 본다")
  void densestBlock() {
    String html =
        """
        <html><head><title>Plain</title></head><body>
        <div id="top"><p>짧은 메뉴</p></div>
        <div id="content"><p>%s</p><p>%s</p><p>%s</p><p>%s</p></div>
        <div id="side"><p>오늘의 날씨</p></div>
        </body></html>
        """
            .formatted(PARAGRAPH, PARAGRAPH + " 둘째", PARAGRAPH + " 셋째", PARAGRAPH + " 넷째");
    Article article =
        ArticleExtractor.extract(
            "https://blog.example.com/p",
            "blog.example.com",
            html.getBytes(StandardCharsets.UTF_8));
    assertThat(article.site()).isEqualTo("blog.example.com");
    assertThat(article.title()).isEqualTo("Plain");
    assertThat(article.language()).isNull();
    assertThat(article.text()).contains("둘째").contains("셋째").doesNotContain("짧은 메뉴");
  }

  @Test
  @DisplayName("기사로 만들기: 본문이 너무 짧으면(유료 기사 미리 보기 등) 본문을 못 찾았다고 알린다")
  void paywall() {
    String html = "<html><body><article><p>구독자만 볼 수 있는 기사예요.</p></article></body></html>";
    assertThatThrownBy(
            () ->
                ArticleExtractor.extract(
                    "https://paid.example.com/a",
                    "paid.example.com",
                    html.getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ArticleException.class)
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.NO_BODY);
  }

  @Test
  @DisplayName("기사로 만들기: 본문은 길이 한도에서 줄 경계로 자른다")
  void cutsLongBody() {
    StringBuilder body = new StringBuilder("<html><body><article>");
    for (int i = 0; i < 300; i++) {
      body.append("<p>").append(i).append("번째 문단 ").append(PARAGRAPH).append("</p>");
    }
    body.append("</article></body></html>");
    Article article =
        ArticleExtractor.extract(
            "https://long.example.com/a",
            "long.example.com",
            body.toString().getBytes(StandardCharsets.UTF_8));
    assertThat(article.text().length()).isLessThanOrEqualTo(ArticleExtractor.MAX_CHARS);
    assertThat(article.text()).startsWith("0번째 문단").endsWith(".");
  }

  @Test
  @DisplayName("페이지 언어는 ko, en만 알아본다")
  void language() {
    assertThat(ArticleExtractor.language("ko-KR")).isEqualTo("ko");
    assertThat(ArticleExtractor.language("en-US")).isEqualTo("en");
    assertThat(ArticleExtractor.language("ja")).isNull();
    assertThat(ArticleExtractor.language(null)).isNull();
  }
}
