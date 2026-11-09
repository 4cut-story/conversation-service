package net.java21.fourcut.conversation.article;

/**
 * 기사 URL에서 골라낸 본문.
 *
 * @param url 마지막으로 받은 주소 (넘겨주기를 따라간 뒤)
 * @param title 기사 제목. 못 찾으면 사이트 이름
 * @param site 사이트 이름 (og:site_name, 없으면 호스트)
 * @param language 페이지 언어 (ko, en). 모르면 null
 * @param text 본문. 문단마다 한 줄, {@link ArticleExtractor#MAX_CHARS}자까지
 */
public record Article(String url, String title, String site, String language, String text) {}
