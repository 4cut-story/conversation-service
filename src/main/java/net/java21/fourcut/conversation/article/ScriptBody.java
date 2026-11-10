package net.java21.fourcut.conversation.article;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 본문을 화면 HTML이 아니라 스크립트 속 데이터로만 보내는 기사 페이지(브라우저에서 그리는 사이트)에서 본문을 찾는다. JSON-LD의 {@code articleBody}와
 * Next.js가 페이지에 심어 두는 데이터({@code self.__next_f.push})의 {@code articleContent} 같은 값을 읽어, 그 안의 HTML에서
 * 글만 고른다.
 */
final class ScriptBody implements BodySource {

  /** 본문을 담는 흔한 키. 값은 글이거나 HTML 조각이다. */
  private static final Pattern BODY_KEY =
      Pattern.compile(
          "\"(articleBody|articleContent|articleText|contentHtml|bodyHtml)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

  /** Next.js App Router가 심는 데이터 조각: self.__next_f.push([1,"..."]). */
  private static final Pattern NEXT_PUSH =
      Pattern.compile("self\\.__next_f\\.push\\((\\[.*])\\)\\s*;?\\s*$", Pattern.DOTALL);

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** 스크립트에서 찾은 본문 중 가장 긴 것. 없으면 빈 글. 스크립트를 걷어 내기 전 문서를 읽는다. */
  @Override
  public String find(ArticlePage page) {
    Document doc = page.raw();
    String best = "";
    for (String data : texts(doc)) {
      Matcher m = BODY_KEY.matcher(data);
      while (m.find()) {
        String value = unescape(m.group(2));
        if (value == null) {
          continue;
        }
        String text = ArticleExtractor.lines(Jsoup.parseBodyFragment(value).body());
        if (text.length() > best.length()) {
          best = text;
        }
      }
    }
    return best;
  }

  /** 스크립트 글. Next.js 데이터 조각은 한 번 풀어(JS 문자열 → 원래 글) 함께 넣는다. */
  private static List<String> texts(Document doc) {
    List<String> out = new ArrayList<>();
    StringBuilder next = new StringBuilder();
    for (Element script : doc.select("script")) {
      String data = script.data();
      Matcher push = NEXT_PUSH.matcher(data.strip());
      if (push.find()) {
        try {
          for (JsonNode part : JSON.readTree(push.group(1))) {
            if (part.isString()) {
              next.append(part.asString());
            }
          }
        } catch (JacksonException e) {
          // 모양이 다르면 그대로 본다
          out.add(data);
        }
      } else {
        out.add(data);
      }
    }
    if (!next.isEmpty()) {
      out.add(next.toString());
    }
    return out;
  }

  /** JSON 문자열 값(따옴표 안)을 원래 글로 푼다. 잘못된 값이면 null. */
  private static String unescape(String raw) {
    try {
      return JSON.readValue("\"" + raw + "\"", String.class);
    } catch (JacksonException e) {
      return null;
    }
  }
}
