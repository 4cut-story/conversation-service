package net.java21.fourcut.conversation.article;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 만화로 만들지 않는 기사인지 가린다 (004 FR-069, 기사 활용 지침 4). 칼럼·사설·인터뷰·기고처럼 글쓴이의 표현이 핵심인 글은 각색하면 원문의 2차적저작물이 될 수
 * 있고, 사진·영상 위주 기사는 옮길 사실이 거의 없이 남의 사진을 설명하게 된다. 제목 머리말([사설], [포토] 등), 주소 경로(/opinion/, /photo/ 등),
 * 기사 분류(article:section)를 본다. 유료·로그인 기사는 본문 길이로 따로 거른다({@link ArticleExtractor#MIN_CHARS}).
 */
public final class ExcludedArticle {

  private static final String WORDS_KO =
      "사설|칼럼|컬럼|인터뷰|기고|시론|오피니언|논단|시평|기자\\s*수첩|특별\\s*기고|포토|사진|화보|영상|동영상|카드\\s*뉴스|그래픽\\s*뉴스";

  private static final String WORDS_EN =
      "opinion|editorial|op-ed|column|commentary|interview|photo|photos|gallery|video|videos|slideshow";

  /** 제목 머리말: [사설], 【칼럼】, <인터뷰>, [홍길동 칼럼], [포토], [영상] 등. */
  private static final Pattern TITLE_TAG =
      Pattern.compile(
          "^\\s*[\\[【<〈(]\\s*[^\\]】>〉)]{0,20}?(" + WORDS_KO + ")\\s*[^\\]】>〉)]{0,10}[\\]】>〉)]",
          Pattern.CASE_INSENSITIVE);

  /** 영어 제목 머리말: "Opinion:", "Editorial |", "Interview -", "Photos:". */
  private static final Pattern TITLE_EN =
      Pattern.compile("^\\s*(" + WORDS_EN + ")\\s*[:|\\-–]", Pattern.CASE_INSENSITIVE);

  /** 주소 경로 마디. */
  private static final Pattern PATH =
      Pattern.compile(
          "(^|/)(opinions?|editorials?|columns?|oped|op-ed|interviews?|commentary|photos?|gallery"
              + "|galleries|videos?|slideshow|cardnews)(/|$|\\.)",
          Pattern.CASE_INSENSITIVE);

  /** 기사 분류. */
  private static final Pattern SECTION =
      Pattern.compile("(" + WORDS_KO + ")|\\b(" + WORDS_EN + ")s?\\b", Pattern.CASE_INSENSITIVE);

  private ExcludedArticle() {}

  /** 제목·주소·분류 중 하나라도 만들지 않는 기사를 가리키면 true. 모두 null이어도 된다. */
  public static boolean matches(String title, String url, String section) {
    return title(title) || url(url) || (section != null && SECTION.matcher(section).find());
  }

  static boolean title(String title) {
    return title != null && (TITLE_TAG.matcher(title).find() || TITLE_EN.matcher(title).find());
  }

  static boolean url(String url) {
    if (url == null || url.isBlank()) {
      return false;
    }
    String path;
    try {
      path = URI.create(url.strip()).getPath();
    } catch (IllegalArgumentException e) {
      return false;
    }
    return path != null && PATH.matcher(path.toLowerCase(Locale.ROOT)).find();
  }
}
