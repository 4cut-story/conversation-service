package net.java21.fourcut.conversation.article;

/**
 * 기사 페이지에서 본문을 찾는 방법 하나. 언론사·기관마다 본문을 담는 방식이 달라서 방법마다 구현을 따로 둔다. {@link ArticleExtractor}는 등록된 방법을
 * 차례로 써 보고, 처음으로 충분히 긴 본문을 낸 것을 쓴다. 새 사이트를 받으려면 구현을 하나 더해 {@link ArticleExtractor#SOURCES}에 넣는다.
 */
interface BodySource {

  /** 이 페이지에 쓰는 방법인가. 기본은 모든 사이트에 쓴다. */
  default boolean supports(ArticlePage page) {
    return true;
  }

  /** 찾은 본문(한 줄에 한 문단). 못 찾으면 빈 글. */
  String find(ArticlePage page);
}
