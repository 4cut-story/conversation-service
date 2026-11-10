package net.java21.fourcut.conversation.article;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * 기사 HTML에서 제목과 본문만 골라낸다. 스크립트·메뉴·광고·댓글·관련 기사 블록을 걷어 낸 뒤, 본문 찾는 방법({@link BodySource})을 차례로 써 본다.
 * 기자 이름·전자우편·저작권 줄은 뺀다.
 */
final class ArticleExtractor {

  /** LLM에 넣는 본문 길이 한도. 콘티는 장면 몇 개만 쓰므로 앞부분이면 충분하다. */
  static final int MAX_CHARS = 6000;

  /** 이보다 짧으면 본문을 못 찾은 것으로 본다 (유료 기사 미리 보기, 목록 페이지 등). */
  static final int MIN_CHARS = 200;

  /**
   * 본문 찾는 방법. 앞에 있을수록 먼저 쓴다: 본문 자리를 아는 사이트, 일반 HTML 규칙, 스크립트 속 데이터(브라우저에서 본문을 그리는 사이트, 예: JTBC).
   */
  static final List<BodySource> SOURCES =
      List.of(
          new SiteBody(Set.of("mods.go.kr"), ".board_content"), new MarkupBody(), new ScriptBody());

  private static final String JUNK_TAGS =
      "script, style, noscript, template, iframe, svg, canvas, form, nav, header, footer, aside,"
          + " button, select, input, textarea, figcaption, video, audio, object, embed";

  /** id·class에 이 낱말이 들어간 블록은 본문이 아니다. */
  private static final Pattern JUNK_NAME =
      Pattern.compile(
          "(^|[\\s_-])(ad|ads|adv|advert\\w*|banner|sponsor\\w*|promo\\w*|share|sns|social|comment\\w*"
              + "|reply|related|recommend\\w*|popular|ranking|most|subscribe|newsletter|popup|modal"
              + "|cookie|gnb|lnb|menu|nav\\w*|sidebar|side|breadcrumb\\w*|footer|copyright|tag\\w*"
              + "|byline|reporter|journalist|keyword\\w*|hidden|sr-only|blind)([\\s_-]|$)",
          Pattern.CASE_INSENSITIVE);

  private static final Set<String> BLOCKS =
      Set.of(
          "p",
          "div",
          "section",
          "article",
          "h1",
          "h2",
          "h3",
          "h4",
          "h5",
          "h6",
          "li",
          "blockquote",
          "tr",
          "br",
          "dd",
          "dt",
          "pre",
          "table",
          "ul",
          "ol");

  /** 빼는 줄: 기자·전자우편·저작권·사진 설명·안내 문구. */
  private static final Pattern NOISE_LINE =
      Pattern.compile(
          "[\\w.+-]+@[\\w-]+\\.[\\w.]+|무단\\s*(전재|복제)|재배포\\s*금지|copyright|ⓒ|©|all rights reserved"
              + "|^\\s*[▶■▲△☞※]|기자\\s*$|^\\s*\\[?사진|^\\s*관련\\s*기사",
          Pattern.CASE_INSENSITIVE);

  /** 짧은 줄일 때만 빼는 안내 문구 (인용한 말 속 "좋아요"는 남긴다). */
  private static final Pattern SHORT_NOISE = Pattern.compile("구독|좋아요|공유|댓글|기사\\s*추천|많이 본");

  private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u200b]+");

  private ArticleExtractor() {}

  static Article extract(String url, String host, byte[] html) {
    Document doc;
    try {
      doc = Jsoup.parse(new ByteArrayInputStream(html), null, url);
    } catch (IOException e) {
      throw new ArticleException(ArticleException.Reason.NOT_HTML, "parse", e);
    }
    String site = firstNonBlank(meta(doc, "og:site_name"), host.replaceFirst("^www\\.", ""));
    String title =
        firstNonBlank(
            meta(doc, "og:title"),
            meta(doc, "twitter:title"),
            doc.title(),
            doc.selectFirst("h1") == null ? null : doc.selectFirst("h1").text(),
            site);
    String language =
        language(doc.selectFirst("html") == null ? "" : doc.selectFirst("html").attr("lang"));
    if (ExcludedArticle.matches(title, url, meta(doc, "article:section"))) {
      throw new ArticleException(ArticleException.Reason.EXCLUDED, "제외 기사");
    }
    Document cleaned = doc.clone();
    clean(cleaned);
    String text = body(new ArticlePage(host, doc, cleaned));
    if (text.length() < MIN_CHARS) {
      throw new ArticleException(ArticleException.Reason.NO_BODY, text.length() + "자");
    }
    return new Article(url, cut(collapse(title), 200), cut(collapse(site), 100), language, text);
  }

  /** 쓸 수 있는 방법을 차례로 써 보고, 처음으로 {@link #MIN_CHARS} 이상인 본문을 낸다. 모두 짧으면 가장 긴 것. */
  static String body(ArticlePage page) {
    String best = "";
    for (BodySource source : SOURCES) {
      if (!source.supports(page)) {
        continue;
      }
      String text = source.find(page);
      if (text.length() >= MIN_CHARS) {
        return text;
      }
      if (text.length() > best.length()) {
        best = text;
      }
    }
    return best;
  }

  private static void clean(Document doc) {
    doc.select(JUNK_TAGS).remove();
    List<Element> junk = new ArrayList<>();
    for (Element e : doc.getAllElements()) {
      if (e.equals(doc.body()) || "html".equals(e.tagName()) || "article".equals(e.tagName())) {
        continue;
      }
      String names = e.id() + " " + e.className();
      if (!names.isBlank()
          && JUNK_NAME.matcher(names).find()
          && !e.is("[itemprop=articleBody]")
          && e.selectFirst("[itemprop=articleBody]") == null) {
        junk.add(e);
      }
      if (e.hasAttr("hidden") || "true".equals(e.attr("aria-hidden"))) {
        junk.add(e);
      }
    }
    junk.forEach(Element::remove);
  }

  /** 블록마다 한 줄로 글을 모으고, 잡음 줄을 빼고, 길이 한도에서 줄 경계로 자른다. */
  static String lines(Element root) {
    StringBuilder raw = new StringBuilder();
    walk(root, raw);
    StringBuilder out = new StringBuilder();
    String previous = null;
    for (String line : raw.toString().split("\n")) {
      String text = collapse(line);
      if (text.length() < 2
          || NOISE_LINE.matcher(text).find()
          || (text.length() < 40 && SHORT_NOISE.matcher(text).find())
          || text.equals(previous)) {
        continue;
      }
      if (out.length() + text.length() + 1 > MAX_CHARS) {
        if (out.isEmpty()) {
          out.append(text, 0, MAX_CHARS);
        }
        break;
      }
      out.append(text).append('\n');
      previous = text;
    }
    return out.toString().strip();
  }

  private static void walk(Node node, StringBuilder out) {
    for (Node child : node.childNodes()) {
      if (child instanceof TextNode t) {
        out.append(t.text());
      } else if (child instanceof Element e) {
        boolean block = BLOCKS.contains(e.normalName());
        if (block) {
          out.append('\n');
        }
        walk(e, out);
        if (block) {
          out.append('\n');
        }
      }
    }
  }

  private static String meta(Document doc, String name) {
    Element e = doc.selectFirst("meta[property=" + name + "], meta[name=" + name + "]");
    return e == null ? null : e.attr("content");
  }

  static String language(String lang) {
    String l = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
    if (l.startsWith("ko")) {
      return "ko";
    }
    return l.startsWith("en") ? "en" : null;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  private static String collapse(String text) {
    return SPACES.matcher(text).replaceAll(" ").strip();
  }

  private static String cut(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max);
  }
}
