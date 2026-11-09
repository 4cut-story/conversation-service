package net.java21.fourcut.conversation.article;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * 서버가 대신 여는 주소의 SSRF 검사. http·https와 기본 포트(80, 443)만 받고, 호스트를 미리 풀어 나온 주소가 하나라도 내부망·예약 대역이면 막는다.
 * 넘겨주기(3xx)를 따라갈 때도 매번 다시 검사한다.
 */
final class SafeUrl {

  /** 주소 길이 한도. */
  static final int MAX_LENGTH = 2048;

  private SafeUrl() {}

  /** 호스트 이름을 IP 주소로 푼다. 테스트에서 바꿔 끼운다. */
  interface Resolver {
    List<InetAddress> resolve(String host) throws UnknownHostException;

    Resolver SYSTEM = host -> List.of(InetAddress.getAllByName(host));
  }

  /** 검사한 주소: 정리한 URI와 풀린 IP 주소. */
  record Checked(URI uri, List<InetAddress> addresses) {}

  /**
   * 주소를 검사한다.
   *
   * @param allowPrivate 테스트 전용. true면 내부망 주소도 받는다
   * @throws ArticleException 막는 주소면 BLOCKED_URL, 호스트를 풀 수 없으면 UNREACHABLE
   */
  static Checked check(String raw, Resolver resolver, boolean allowPrivate) {
    if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
      throw blocked("길이");
    }
    URI uri;
    try {
      uri = new URI(raw.strip()).normalize();
    } catch (URISyntaxException e) {
      throw blocked("형식");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("http") && !scheme.equals("https")) {
      throw blocked("scheme");
    }
    if (uri.getRawUserInfo() != null) {
      throw blocked("userinfo");
    }
    int port = uri.getPort();
    if (port != -1 && port != 80 && port != 443 && !allowPrivate) {
      throw blocked("port");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw blocked("host");
    }
    host = host.toLowerCase(Locale.ROOT);
    if (host.startsWith("[") && host.endsWith("]")) {
      host = host.substring(1, host.length() - 1);
    }
    if (!allowPrivate && internalName(host)) {
      throw blocked("host");
    }
    List<InetAddress> addresses;
    try {
      // IP 주소를 바로 넣었으면 이름 풀기 없이 그 주소를 검사한다
      addresses = literal(host) ? List.of(InetAddress.getByName(host)) : resolver.resolve(host);
    } catch (UnknownHostException e) {
      throw new ArticleException(ArticleException.Reason.UNREACHABLE, "dns");
    }
    if (addresses.isEmpty()) {
      throw new ArticleException(ArticleException.Reason.UNREACHABLE, "dns");
    }
    if (!allowPrivate && addresses.stream().anyMatch(SafeUrl::internal)) {
      throw blocked("address");
    }
    return new Checked(uri, addresses);
  }

  /** IPv4(점 네 개 숫자)나 IPv6 주소 표기인지. */
  static boolean literal(String host) {
    return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
  }

  /** 점이 없는 이름(클러스터 서비스 이름 등)과 내부용 최상위 이름. */
  static boolean internalName(String host) {
    if (host.indexOf('.') < 0 && host.indexOf(':') < 0) {
      return true;
    }
    return host.equals("localhost")
        || host.endsWith(".localhost")
        || host.endsWith(".local")
        || host.endsWith(".internal")
        || host.endsWith(".cluster.local")
        || host.endsWith(".svc")
        || host.endsWith(".lan")
        || host.endsWith(".home.arpa");
  }

  /** 공개 인터넷 주소가 아닌지 (루프백, 사설망, 링크 로컬, CGNAT, 멀티캐스트, 예약 대역, IPv6 ULA와 그 안에 든 IPv4). */
  static boolean internal(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] b = address.getAddress();
    if (address instanceof Inet4Address) {
      return internalV4(b);
    }
    if (address instanceof Inet6Address) {
      int first = b[0] & 0xff;
      // fc00::/7 고유 로컬, fec0::/10 옛 사이트 로컬
      if ((first & 0xfe) == 0xfc || (first == 0xfe && (b[1] & 0xc0) == 0xc0)) {
        return true;
      }
      // ::ffff:a.b.c.d (IPv4 매핑), ::a.b.c.d (IPv4 호환), 64:ff9b::/96 (NAT64)
      boolean zeros = true;
      for (int i = 0; i < 10; i++) {
        zeros &= b[i] == 0;
      }
      boolean mapped = zeros && (b[10] & 0xff) == 0xff && (b[11] & 0xff) == 0xff;
      boolean compatible = zeros && b[10] == 0 && b[11] == 0;
      boolean nat64 =
          (b[0] & 0xff) == 0x00
              && (b[1] & 0xff) == 0x64
              && (b[2] & 0xff) == 0xff
              && (b[3] & 0xff) == 0x9b;
      if (mapped || compatible || nat64) {
        return internalV4(new byte[] {b[12], b[13], b[14], b[15]});
      }
      // 2001:db8::/32 문서용
      return (b[0] & 0xff) == 0x20
          && (b[1] & 0xff) == 0x01
          && (b[2] & 0xff) == 0x0d
          && (b[3] & 0xff) == 0xb8;
    }
    return true;
  }

  private static boolean internalV4(byte[] b) {
    int a0 = b[0] & 0xff;
    int a1 = b[1] & 0xff;
    int a2 = b[2] & 0xff;
    return a0 == 0 // 0.0.0.0/8
        || a0 == 10
        || a0 == 127
        || (a0 == 100 && a1 >= 64 && a1 <= 127) // CGNAT 100.64.0.0/10
        || (a0 == 169 && a1 == 254)
        || (a0 == 172 && a1 >= 16 && a1 <= 31)
        || (a0 == 192 && a1 == 168)
        || (a0 == 192 && a1 == 0 && a2 == 0) // 192.0.0.0/24
        || (a0 == 192 && a1 == 0 && a2 == 2) // 문서용
        || (a0 == 198 && (a1 == 18 || a1 == 19)) // 벤치마크 198.18.0.0/15
        || (a0 == 198 && a1 == 51 && a2 == 100)
        || (a0 == 203 && a1 == 0 && a2 == 113)
        || a0 >= 224; // 멀티캐스트, 예약, 브로드캐스트
  }

  private static ArticleException blocked(String detail) {
    return new ArticleException(ArticleException.Reason.BLOCKED_URL, detail);
  }
}
