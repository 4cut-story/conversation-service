package net.java21.fourcut.conversation.article;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** 일반 HTML 규칙으로 찾는 본문. 알려진 본문 자리(articleBody, article 등)를 먼저 보고, 없으면 긴 글이 가장 많이 모인 블록을 본문으로 본다. */
final class MarkupBody implements BodySource {

  /** 알려진 본문 자리. 앞에 있을수록 먼저 본다. */
  private static final List<String> BODY_SELECTORS =
      List.of(
          "[itemprop=articleBody]",
          "#dic_area",
          "#articleBodyContents",
          "#article-view-content-div",
          "#articletxt",
          "#articleBody",
          "#news_body_area",
          ".article_body",
          ".article-body",
          ".article_view",
          ".news_body",
          ".story-body",
          "article",
          "[role=main]",
          "main");

  @Override
  public String find(ArticlePage page) {
    return body(page.cleaned());
  }

  private static String body(Document doc) {
    for (String selector : BODY_SELECTORS) {
      for (Element candidate : doc.select(selector)) {
        String text = ArticleExtractor.lines(candidate);
        if (text.length() >= ArticleExtractor.MIN_CHARS) {
          return text;
        }
      }
    }
    Element best = densest(doc);
    return best == null ? "" : ArticleExtractor.lines(best);
  }

  /**
   * 긴 문단(40자 이상)이 가장 많이 모인 블록. 문단 길이를 부모에 다 더하고 조부모에 절반을 더한다. 줄바꿈(br)이나 글만으로 문단을 나눈 글 상자(문단 태그 없는
   * div)는 그 상자 자신을 블록으로 본다.
   */
  private static Element densest(Document doc) {
    Map<Element, Integer> scores = new HashMap<>();
    for (Element e : doc.select("p, div > br, div:not(:has(> br))")) {
      boolean paragraph = "p".equals(e.normalName());
      Element block = "br".equals(e.normalName()) ? e.parent() : e;
      int length = block.ownText().length();
      if (length < 40) {
        continue;
      }
      Element parent = paragraph ? block.parent() : block;
      if (parent != null) {
        scores.merge(parent, length, Integer::sum);
        if (parent.parent() != null) {
          scores.merge(parent.parent(), length / 2, Integer::sum);
        }
      }
    }
    return scores.entrySet().stream()
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(doc.body());
  }
}
