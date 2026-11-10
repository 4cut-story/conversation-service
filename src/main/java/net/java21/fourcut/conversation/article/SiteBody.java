package net.java21.fourcut.conversation.article;

import java.util.Set;
import org.jsoup.nodes.Element;

/**
 * 본문 자리를 아는 사이트의 본문. 일반 규칙으로 못 찾는 사이트마다 하나씩 등록한다. 예: 국가데이터처 게시판은 문단 태그 없이 글 상자 하나({@code
 * .board_content})에 본문을 넣는다.
 *
 * @param domains 쓰는 도메인 (하위 도메인 포함)
 * @param selector 본문 자리 CSS 선택자
 */
record SiteBody(Set<String> domains, String selector) implements BodySource {

  SiteBody {
    domains = Set.copyOf(domains);
  }

  @Override
  public boolean supports(ArticlePage page) {
    return domains.stream().anyMatch(page::isOn);
  }

  @Override
  public String find(ArticlePage page) {
    String best = "";
    for (Element candidate : page.cleaned().select(selector)) {
      String text = ArticleExtractor.lines(candidate);
      if (text.length() > best.length()) {
        best = text;
      }
    }
    return best;
  }
}
