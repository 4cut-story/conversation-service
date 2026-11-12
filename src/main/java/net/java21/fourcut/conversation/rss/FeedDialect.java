package net.java21.fourcut.conversation.rss;

import java.util.List;

/**
 * 구독 사투리가 자동(AUTO)일 때 기사마다 사투리를 고른다. 기사 주소로 고르므로 같은 기사는 늘 같은 사투리가 된다. 절반은 표준어로 두고, 나머지 절반을 다섯 사투리에
 * 고르게 나눈다. 흑백 신문 그림체로 고정한 구독은 사실을 전하는 만평 톤이라 사투리를 넣지 않는다.
 */
final class FeedDialect {

  /** 저장값: 기사마다 자동으로 고른다. */
  static final String AUTO = "AUTO";

  /** 자동일 때 고르는 칸. null은 표준어(사투리 없음). */
  static final List<String> CHOICES =
      java.util.Arrays.asList(
          "GYEONGSANG", "JEOLLA", "CHUNGCHEONG", "GANGWON", "SEOUL", null, null, null, null, null);

  private FeedDialect() {}

  /**
   * 이 기사로 만들 만화의 사투리.
   *
   * @param dialect 구독에 저장된 사투리. null이면 없음, AUTO면 자동
   * @param concept 구독에 저장된 그림체. null이면 자동
   * @param link 기사 주소
   * @return 사투리 이름. 없음이면 null
   */
  static String pick(String dialect, String concept, String link) {
    if (!AUTO.equals(dialect)) {
      return dialect;
    }
    if ("NEWSPAPER".equals(concept) || link == null) {
      return null;
    }
    return CHOICES.get(Math.floorMod(link.hashCode(), CHOICES.size()));
  }
}
