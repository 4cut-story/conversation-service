package net.java21.fourcut.conversation.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeUrlTest {

  private static SafeUrl.Resolver to(String ip) {
    return host -> List.of(InetAddress.getByName(ip));
  }

  @Test
  @DisplayName("기사로 만들기: 공개 주소의 http·https 기사는 연다")
  void publicAddress() {
    SafeUrl.Checked checked =
        SafeUrl.check("https://news.example.com/article/1?x=1", to("93.184.216.34"), false);
    assertThat(checked.uri().getHost()).isEqualTo("news.example.com");
    assertThat(SafeUrl.check("http://news.example.com:80/a", to("8.8.8.8"), false)).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "10.1.2.3",
        "172.16.0.5",
        "192.168.0.10",
        "169.254.169.254",
        "100.64.0.1",
        "0.0.0.0",
        "224.0.0.1",
        "198.18.0.1",
        "::1",
        "fc00::1",
        "fe80::1",
        "::ffff:127.0.0.1",
        "64:ff9b::a00:1"
      })
  @DisplayName("기사로 만들기: 내부망·예약 주소로 풀리는 호스트는 막는다 (SSRF)")
  void internalAddress(String ip) {
    assertThatThrownBy(() -> SafeUrl.check("https://evil.example.com/", to(ip), false))
        .isInstanceOf(ArticleException.class)
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.BLOCKED_URL);
  }

  @Test
  @DisplayName("기사로 만들기: 풀린 주소 중 하나라도 내부망이면 막는다")
  void anyInternal() throws UnknownHostException {
    InetAddress good = InetAddress.getByName("8.8.8.8");
    InetAddress bad = InetAddress.getByName("10.0.0.1");
    assertThatThrownBy(
            () -> SafeUrl.check("https://mixed.example.com/", host -> List.of(good, bad), false))
        .isInstanceOf(ArticleException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:///etc/passwd",
        "ftp://example.com/a",
        "gopher://example.com/",
        "https://user:pw@example.com/",
        "https://example.com:8080/",
        "http://localhost/",
        "http://content-service/internal/comics",
        "http://minio.storage.svc.cluster.local/",
        "http://printer.local/",
        "not a url",
        ""
      })
  @DisplayName("기사로 만들기: http·https와 기본 포트가 아니거나 내부용 이름이면 막는다")
  void blockedShape(String url) {
    assertThatThrownBy(() -> SafeUrl.check(url, to("8.8.8.8"), false))
        .isInstanceOf(ArticleException.class)
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.BLOCKED_URL);
  }

  @Test
  @DisplayName("기사로 만들기: 너무 긴 주소와 풀리지 않는 호스트는 열지 않는다")
  void tooLongOrUnknown() {
    String longUrl = "https://example.com/" + "a".repeat(SafeUrl.MAX_LENGTH);
    assertThatThrownBy(() -> SafeUrl.check(longUrl, to("8.8.8.8"), false))
        .isInstanceOf(ArticleException.class);
    assertThatThrownBy(
            () ->
                SafeUrl.check(
                    "https://nowhere.example.com/",
                    host -> {
                      throw new UnknownHostException(host);
                    },
                    false))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.UNREACHABLE);
    assertThatThrownBy(() -> SafeUrl.check("https://empty.example.com/", host -> List.of(), false))
        .extracting(e -> ((ArticleException) e).reason())
        .isEqualTo(ArticleException.Reason.UNREACHABLE);
  }

  @Test
  @DisplayName("테스트 전용 설정에서는 내부망 주소도 연다")
  void allowPrivate() {
    assertThat(SafeUrl.check("http://127.0.0.1:8099/a", to("127.0.0.1"), true)).isNotNull();
  }
}
