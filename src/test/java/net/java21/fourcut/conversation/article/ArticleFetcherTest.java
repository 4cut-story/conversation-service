package net.java21.fourcut.conversation.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArticleFetcherTest {

  private HttpServer server;
  private String base;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/article",
        ex -> {
          byte[] body = ArticleExtractorTest.articleHtml().getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.createContext(
        "/moved",
        ex -> {
          ex.getResponseHeaders().add("Location", "/article");
          ex.sendResponseHeaders(302, -1);
          ex.close();
        });
    server.createContext(
        "/loop",
        ex -> {
          ex.getResponseHeaders().add("Location", "/loop");
          ex.sendResponseHeaders(302, -1);
          ex.close();
        });
    server.createContext(
        "/no-location",
        ex -> {
          ex.sendResponseHeaders(302, -1);
          ex.close();
        });
    server.createContext(
        "/image",
        ex -> {
          ex.getResponseHeaders().add("Content-Type", "image/png");
          ex.sendResponseHeaders(200, 3);
          ex.getResponseBody().write(new byte[] {1, 2, 3});
          ex.close();
        });
    server.createContext(
        "/gone",
        ex -> {
          ex.sendResponseHeaders(404, -1);
          ex.close();
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  /** 내부망은 테스트 서버(127.0.0.1)만 열고, 그 밖의 내부 주소 검사는 그대로 둔다. */
  private ArticleFetcher fetcher(boolean allowPrivate) {
    return new ArticleFetcher(
        new ArticleProperties(Duration.ofSeconds(5), 1_000_000, 3, allowPrivate),
        host -> List.of(InetAddress.getByName(host)));
  }

  @Test
  @DisplayName("기사로 만들기: 기사 HTML을 받고 넘겨주기를 따라간다")
  void fetchesAndFollows() {
    ArticleFetcher.Page page = fetcher(true).fetch(base + "/moved");
    assertThat(page.uri().getPath()).isEqualTo("/article");
    assertThat(new String(page.body(), StandardCharsets.UTF_8)).contains("떡집");
  }

  @Test
  @DisplayName("기사로 만들기: 운영 설정에서는 127.0.0.1 같은 내부 주소를 열지 않는다 (SSRF)")
  void blocksLoopback() {
    assertThatThrownBy(() -> fetcher(false).fetch(base + "/article"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.BLOCKED_URL);
  }

  @Test
  @DisplayName("기사로 만들기: 클라우드 메타데이터 주소는 IP로 바로 넣어도 열지 않는다")
  void blocksRedirectToMetadata() {
    ArticleFetcher strict =
        new ArticleFetcher(
            new ArticleProperties(Duration.ofSeconds(5), 1_000_000, 3, false),
            host -> List.of(InetAddress.getByName("8.8.8.8")));
    assertThatThrownBy(() -> strict.fetch("http://169.254.169.254/"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.BLOCKED_URL);
  }

  @Test
  @DisplayName("기사로 만들기: 끝없는 넘겨주기, 위치 없는 넘겨주기, 404, HTML이 아닌 응답은 실패로 알린다")
  void failures() {
    ArticleFetcher f = fetcher(true);
    assertThatThrownBy(() -> f.fetch(base + "/loop"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.UNREACHABLE);
    assertThatThrownBy(() -> f.fetch(base + "/no-location"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.UNREACHABLE);
    assertThatThrownBy(() -> f.fetch(base + "/gone"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.UNREACHABLE);
    assertThatThrownBy(() -> f.fetch(base + "/image"))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.NOT_HTML);
  }

  @Test
  @DisplayName("기사로 만들기: 기사를 읽어 제목과 본문을 돌려주고, 실패는 사용자 문장으로 알린다")
  void reader() {
    ArticleProperties properties = new ArticleProperties(Duration.ofSeconds(5), 1_000_000, 3, true);
    ArticleReader reader =
        new ArticleReader(
            new ArticleFetcher(properties, host -> List.of(InetAddress.getByName(host))),
            properties);
    Article article = reader.read(base + "/article");
    assertThat(article.title()).isEqualTo("30년 떡집의 마지막 날");
    assertThat(article.url()).endsWith("/article");
    assertThatThrownBy(() -> reader.read(base + "/gone"))
        .isInstanceOf(ArticleException.class)
        .extracting(e -> ((ArticleException) e).userMessage())
        .isEqualTo(ArticleException.Reason.UNREACHABLE.message());
  }
}
