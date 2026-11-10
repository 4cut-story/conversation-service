package net.java21.fourcut.conversation.article;

import java.util.Locale;
import org.jsoup.nodes.Document;

/**
 * 본문을 찾을 기사 페이지.
 *
 * @param host 호스트 (소문자, 앞의 www. 없이)
 * @param raw 받은 그대로의 문서. 스크립트 속 데이터를 읽을 때 쓴다
 * @param cleaned 스크립트·메뉴·광고·댓글 블록을 걷어 낸 문서
 */
record ArticlePage(String host, Document raw, Document cleaned) {

  ArticlePage {
    host = host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
  }

  /** 호스트가 이 도메인이거나 그 하위 도메인인가. */
  boolean isOn(String domain) {
    return host.equals(domain) || host.endsWith("." + domain);
  }
}
