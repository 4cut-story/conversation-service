package net.java21.fourcut.conversation.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.java21.fourcut.conversation.article.ArticleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedParserTest {

  private static final String BASE = "https://news.example.com/rss/all.xml";

  private static FeedParser.Feed parse(String xml) {
    return FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8), BASE);
  }

  private static ArticleException.Reason reason(String xml) {
    try {
      parse(xml);
      throw new AssertionError("피드로 읽혔다");
    } catch (ArticleException e) {
      return e.reason();
    }
  }

  @Test
  @DisplayName("004 FR-050: RSS 2.0의 제목·사이트 주소·기사(guid, 링크, 제목, 발행 시각)를 읽고 atom:link(self)는 건너뛴다")
  void rss2() {
    FeedParser.Feed feed =
        parse(
            """
            ﻿  <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>예시신문 전체기사</title>
                <atom:link href="https://news.example.com/rss/all.xml" rel="self"/>
                <link>https://news.example.com/</link>
                <item>
                  <title>  떡집의   마지막 날 </title>
                  <link>https://news.example.com/a/2</link>
                  <guid isPermaLink="false">news-2</guid>
                  <pubDate>Tue, 10 Nov 2026 09:00:00 +0900</pubDate>
                </item>
                <item>
                  <title>상대 링크</title>
                  <link>/a/1</link>
                  <pubDate>잘못된 날짜</pubDate>
                </item>
                <item><title>링크 없음</title></item>
                <item><title>http 아님</title><link>javascript:alert(1)</link></item>
              </channel>
            </rss>
            """);
    assertThat(feed.title()).isEqualTo("예시신문 전체기사");
    assertThat(feed.siteUrl()).isEqualTo("https://news.example.com/");
    assertThat(feed.entries()).hasSize(2);
    FeedParser.Entry first = feed.entries().get(0);
    assertThat(first.key()).isEqualTo("news-2");
    assertThat(first.link()).isEqualTo("https://news.example.com/a/2");
    assertThat(first.title()).isEqualTo("떡집의 마지막 날");
    assertThat(first.published()).isEqualTo(Instant.parse("2026-11-10T00:00:00Z"));
    FeedParser.Entry second = feed.entries().get(1);
    assertThat(second.key()).isEqualTo("https://news.example.com/a/1");
    assertThat(second.published()).isNull();
  }

  @Test
  @DisplayName("004 FR-050: CDATA 속 제목에 남은 HTML 엔터티(&#x27; 등)를 푼다")
  void titleEntities() {
    FeedParser.Feed feed =
        parse(
            """
            <rss version="2.0"><channel>
              <title><![CDATA[새소식 &amp; 설명]]></title>
              <item>
                <title><![CDATA[「저소득층 물가」(연합뉴스, &#x27;26.8.30) 설명자료]]></title>
                <link>https://news.example.com/a/9</link>
              </item>
            </channel></rss>
            """);
    assertThat(feed.title()).isEqualTo("새소식 & 설명");
    assertThat(feed.entries().get(0).title()).isEqualTo("「저소득층 물가」(연합뉴스, '26.8.30) 설명자료");
  }

  @Test
  @DisplayName("004 FR-050: Atom 피드의 id, alternate 링크, published(없으면 updated)를 읽는다")
  void atom() {
    FeedParser.Feed feed =
        parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>블로그</title>
              <link rel="self" href="https://blog.example.com/feed"/>
              <link href="https://blog.example.com/"/>
              <entry>
                <id>tag:blog,2026:1</id>
                <title>첫 글</title>
                <link rel="alternate" href="https://blog.example.com/1"/>
                <updated>2026-11-09T10:00:00Z</updated>
              </entry>
              <entry>
                <id>tag:blog,2026:2</id>
                <link rel="enclosure" href="https://blog.example.com/a.mp3"/>
              </entry>
            </feed>
            """);
    assertThat(feed.title()).isEqualTo("블로그");
    assertThat(feed.siteUrl()).isEqualTo("https://blog.example.com/");
    assertThat(feed.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.key()).isEqualTo("tag:blog,2026:1");
              assertThat(e.link()).isEqualTo("https://blog.example.com/1");
              assertThat(e.published()).isEqualTo(Instant.parse("2026-11-09T10:00:00Z"));
            });
  }

  @Test
  @DisplayName("004 FR-050: RSS 1.0(RDF)은 channel 옆의 item을 읽고, 제목이 없으면 호스트를 피드 제목으로 쓴다")
  void rdf() {
    FeedParser.Feed feed =
        parse(
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel><link>https://rdf.example.com/</link></channel>
              <item><title>하나</title><link>https://rdf.example.com/1</link><dc:date>2026-11-01T00:00:00+09:00</dc:date></item>
            </rdf:RDF>
            """);
    assertThat(feed.title()).isEqualTo("news.example.com");
    assertThat(feed.entries().get(0).published()).isEqualTo(Instant.parse("2026-10-31T15:00:00Z"));
  }

  @Test
  @DisplayName("004 FR-058: 같은 키의 기사는 한 번만, 한 번에 50개까지만 본다")
  void uniqueAndLimited() {
    StringBuilder items = new StringBuilder();
    for (int i = 0; i < 60; i++) {
      items.append("<item><link>https://n.example.com/").append(i % 55).append("</link></item>");
    }
    FeedParser.Feed feed = parse("<rss><channel><title>t</title>" + items + "</channel></rss>");
    assertThat(feed.entries()).hasSize(FeedParser.MAX_ITEMS);
    assertThat(feed.entries()).extracting(FeedParser.Entry::key).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("004 FR-051: DOCTYPE(외부 엔터티, XXE)이 있는 문서, 웹 페이지, 깨진 XML은 피드가 아니다")
  void rejects() {
    assertThat(
            reason(
                """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss><channel><title>&xxe;</title></channel></rss>
                """))
        .isEqualTo(ArticleException.Reason.NOT_FEED);
    assertThat(reason("<html><body><p>기사</p></body></html>"))
        .isEqualTo(ArticleException.Reason.NOT_FEED);
    assertThat(reason("<rss><item>")).isEqualTo(ArticleException.Reason.NOT_FEED);
    assertThat(reason("<rss version=\"2.0\"></rss>")).isEqualTo(ArticleException.Reason.NOT_FEED);
  }

  @Test
  @DisplayName("004 FR-055: 발행 시각은 RFC 1123과 ISO 8601을 읽고, 못 읽으면 null")
  void dates() {
    assertThat(FeedParser.rfcDate("Mon, 09 Nov 2026 01:02:03 GMT"))
        .isEqualTo(Instant.parse("2026-11-09T01:02:03Z"));
    assertThat(FeedParser.rfcDate("2026-11-09T01:02:03Z"))
        .isEqualTo(Instant.parse("2026-11-09T01:02:03Z"));
    assertThat(FeedParser.isoDate(null)).isNull();
    assertThat(FeedParser.isoDate("어제")).isNull();
  }
}
