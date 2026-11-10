package net.java21.fourcut.conversation.rss;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import net.java21.fourcut.conversation.article.ArticleException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * RSS 2.0, RSS 1.0(RDF), Atom 피드 읽기. DOCTYPE과 외부 엔터티는 받지 않는다(XXE). 기사마다 같은 기사인지 가릴 키(guid·id, 없으면
 * 링크)와 링크, 제목, 발행 시각을 꺼낸다. 링크가 http·https가 아닌 항목은 건너뛴다.
 */
final class FeedParser {

  /** 한 번에 보는 항목 수. */
  static final int MAX_ITEMS = 50;

  static final int MAX_TITLE = 300;
  static final int MAX_FEED_TITLE = 200;
  static final int MAX_URL = 2048;

  private static final String ATOM = "http://www.w3.org/2005/Atom";

  private FeedParser() {}

  /**
   * 피드 기사 하나.
   *
   * @param key 같은 기사인지 가리는 키 (guid·id, 없으면 링크)
   * @param link 기사 주소
   * @param title 기사 제목. 없으면 null
   * @param published 발행 시각. 없거나 읽지 못하면 null
   */
  record Entry(String key, String link, String title, Instant published) {}

  /**
   * 읽은 피드.
   *
   * @param title 피드 제목. 없으면 호스트
   * @param siteUrl 피드가 알려 준 사이트 주소. 없으면 null
   * @param entries 피드 순서대로의 기사 (최대 {@link #MAX_ITEMS}개, 키가 겹치면 앞의 것)
   */
  record Feed(String title, String siteUrl, List<Entry> entries) {}

  /**
   * 피드를 읽는다.
   *
   * @param base 받은 주소. 상대 링크를 풀 때 쓴다
   * @throws ArticleException XML이 아니거나 RSS·Atom이 아니면 NOT_FEED
   */
  static Feed parse(byte[] body, String base) {
    Element root = root(body);
    URI baseUri = URI.create(base);
    String name = root.getLocalName() == null ? root.getNodeName() : root.getLocalName();
    return switch (name.toLowerCase(Locale.ROOT)) {
      case "rss" -> rss(child(root, "channel"), children(child(root, "channel"), "item"), baseUri);
      case "rdf" -> rss(child(root, "channel"), children(root, "item"), baseUri);
      case "feed" -> atom(root, baseUri);
      default -> throw notFeed(name);
    };
  }

  private static Feed rss(Element channel, List<Element> items, URI base) {
    if (channel == null) {
      throw notFeed("channel");
    }
    List<Entry> entries = new ArrayList<>();
    for (Element item : items) {
      String link = resolve(base, rssLink(item));
      String guid = text(item, "guid");
      if (link == null) {
        link = resolve(base, guid);
      }
      String published = text(item, "pubDate");
      Instant at = published == null ? isoDate(text(item, "date")) : rfcDate(published);
      add(entries, guid, link, text(item, "title"), at);
    }
    return feed(text(channel, "title"), resolve(base, rssLink(channel)), entries, base);
  }

  private static Feed atom(Element feed, URI base) {
    List<Entry> entries = new ArrayList<>();
    for (Element entry : children(feed, "entry")) {
      String link = resolve(base, alternate(entry));
      String published = text(entry, "published");
      add(
          entries,
          text(entry, "id"),
          link,
          text(entry, "title"),
          isoDate(published == null ? text(entry, "updated") : published));
    }
    return feed(text(feed, "title"), resolve(base, alternate(feed)), entries, base);
  }

  private static Feed feed(String title, String site, List<Entry> entries, URI base) {
    Map<String, Entry> unique = new LinkedHashMap<>();
    for (Entry e : entries) {
      if (unique.size() >= MAX_ITEMS) {
        break;
      }
      unique.putIfAbsent(e.key(), e);
    }
    String name = title == null ? base.getHost() : cut(title, MAX_FEED_TITLE);
    return new Feed(name == null ? "RSS" : name, site, List.copyOf(unique.values()));
  }

  private static void add(
      List<Entry> entries, String guid, String link, String title, Instant published) {
    if (link == null) {
      return;
    }
    String key = guid == null ? link : cut(guid, MAX_URL);
    entries.add(new Entry(key, link, title == null ? null : cut(title, MAX_TITLE), published));
  }

  /** RSS link: 글이 있는 첫 link. 채널에 함께 오는 atom:link(rel=self, 글 없음)는 건너뛴다. */
  private static String rssLink(Element parent) {
    for (Element link : children(parent, "link")) {
      if (ATOM.equals(link.getNamespaceURI())) {
        continue;
      }
      String text = link.getTextContent().strip();
      if (!text.isEmpty()) {
        return text;
      }
    }
    return null;
  }

  /** Atom link: rel이 없거나 alternate인 첫 링크의 href. */
  private static String alternate(Element parent) {
    for (Element link : children(parent, "link")) {
      String rel = link.getAttribute("rel");
      if (rel.isEmpty() || "alternate".equals(rel)) {
        String href = link.getAttribute("href").strip();
        return href.isEmpty() ? null : href;
      }
    }
    return null;
  }

  private static String resolve(URI base, String raw) {
    if (raw == null) {
      return null;
    }
    try {
      URI uri = base.resolve(raw.strip());
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      String url = uri.toString();
      if (!"http".equals(scheme) && !"https".equals(scheme) || url.length() > MAX_URL) {
        return null;
      }
      return url;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  static Instant rfcDate(String raw) {
    try {
      return ZonedDateTime.parse(raw.strip(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (DateTimeParseException e) {
      return isoDate(raw);
    }
  }

  static Instant isoDate(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(raw.strip()).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static Element root(byte[] body) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setErrorHandler(null);
      Document doc = builder.parse(new ByteArrayInputStream(body, start(body), body.length));
      return doc.getDocumentElement();
    } catch (SAXException | IOException e) {
      throw new ArticleException(ArticleException.Reason.NOT_FEED, "xml", e);
    } catch (ParserConfigurationException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 앞의 BOM·공백을 건너뛴다 (XML 선언 앞에 공백이 있으면 파서가 거절한다). */
  private static int start(byte[] body) {
    int i = 0;
    if (body.length >= 3 && (body[0] & 0xff) == 0xef && (body[1] & 0xff) == 0xbb) {
      i = 3;
    }
    while (i < body.length && body[i] >= 0 && body[i] <= ' ') {
      i++;
    }
    return i;
  }

  private static Element child(Element parent, String name) {
    List<Element> found = children(parent, name);
    return found.isEmpty() ? null : found.get(0);
  }

  private static List<Element> children(Element parent, String name) {
    List<Element> out = new ArrayList<>();
    if (parent == null) {
      return out;
    }
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && name.equalsIgnoreCase(local(e))) {
        out.add(e);
      }
    }
    return out;
  }

  private static String local(Element e) {
    return e.getLocalName() == null ? e.getNodeName() : e.getLocalName();
  }

  /** 첫 자식 요소의 글. 비었으면 null. */
  private static String text(Element parent, String name) {
    Element e = child(parent, name);
    if (e == null) {
      return null;
    }
    String text = e.getTextContent().strip().replaceAll("\\s+", " ");
    return text.isEmpty() ? null : text;
  }

  private static ArticleException notFeed(String detail) {
    return new ArticleException(ArticleException.Reason.NOT_FEED, detail);
  }
}
