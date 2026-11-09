package net.java21.fourcut.conversation.article;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 기사 HTML 받기. 넘겨주기는 자동으로 따라가지 않고 한 번마다 {@link SafeUrl}로 다시 검사한다. 프록시를 거치지 않고, 응답 크기와 시간을 제한한다. 호스트는
 * 검사할 때 한 번 풀고, 연결할 때는 JVM DNS 캐시(기본 30초)가 같은 답을 돌려준다.
 */
@Component
class ArticleFetcher {

  /** 받은 페이지. */
  record Page(URI uri, String contentType, byte[] body) {}

  static final String USER_AGENT =
      "Mozilla/5.0 (compatible; 4cut-story/1.0; +https://4cut.java21.net/about)";

  private final ArticleProperties properties;
  private final SafeUrl.Resolver resolver;
  private final HttpClient client;

  @Autowired
  ArticleFetcher(ArticleProperties properties) {
    this(properties, SafeUrl.Resolver.SYSTEM);
  }

  ArticleFetcher(ArticleProperties properties, SafeUrl.Resolver resolver) {
    this.properties = properties;
    this.resolver = resolver;
    this.client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  Page fetch(String url) {
    String next = url;
    for (int hop = 0; hop <= properties.maxRedirects(); hop++) {
      SafeUrl.Checked checked = SafeUrl.check(next, resolver, properties.allowPrivate());
      HttpResponse<InputStream> response = send(checked.uri());
      int status = response.statusCode();
      if (status < 300 || status >= 400) {
        return page(checked, response);
      }
      String location = response.headers().firstValue("Location").orElse(null);
      close(response);
      if (location == null) {
        throw unreachable("redirect");
      }
      next = checked.uri().resolve(location.strip()).toString();
    }
    throw unreachable("redirects");
  }

  /** 넘겨주기가 아닌 응답: 200 HTML이면 본문을 읽는다. */
  private Page page(SafeUrl.Checked checked, HttpResponse<InputStream> response) {
    int status = response.statusCode();
    if (status != 200) {
      close(response);
      throw unreachable("HTTP " + status);
    }
    String type = response.headers().firstValue("Content-Type").orElse("text/html");
    if (!type.toLowerCase(Locale.ROOT).contains("html")) {
      close(response);
      throw new ArticleException(ArticleException.Reason.NOT_HTML, type);
    }
    return new Page(checked.uri(), type, read(response));
  }

  private HttpResponse<InputStream> send(URI uri) {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(properties.timeout())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
            .header("Accept-Language", "ko,en;q=0.8")
            .GET()
            .build();
    try {
      return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException e) {
      throw unreachable(e.getClass().getSimpleName(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unreachable("interrupted", e);
    }
  }

  /** 크기 한도까지만 읽는다. 기사 본문은 보통 앞부분에 있으므로 넘는 부분은 버린다. */
  private byte[] read(HttpResponse<InputStream> response) {
    try (InputStream in = response.body()) {
      return in.readNBytes(properties.maxBytes());
    } catch (IOException e) {
      throw unreachable("read", e);
    }
  }

  private static void close(HttpResponse<InputStream> response) {
    try {
      response.body().close();
    } catch (IOException ignored) {
      // 버리는 응답이다
    }
  }

  private static ArticleException unreachable(String detail) {
    return unreachable(detail, null);
  }

  private static ArticleException unreachable(String detail, Throwable cause) {
    return new ArticleException(ArticleException.Reason.UNREACHABLE, detail, cause);
  }
}
