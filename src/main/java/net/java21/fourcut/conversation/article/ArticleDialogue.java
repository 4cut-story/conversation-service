package net.java21.fourcut.conversation.article;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 기사를 대화로 각색하는 LLM 작업. 녹음 흐름의 전사 자리에 들어갈 대화를 만든다: 기사 속 당사자·관계자 2~4명이 사건을 두고 나누는 말로, 기사에서는 사실만 가져오고
 * 이야기·인물·대사는 새로 쓴다 (004 기사 활용 지침 1·2·5). 이후 장면 고르기·콘티·줄거리 요약은 녹음 전사와 똑같이 이 대화만 읽고 기사 본문은 보지 않으므로,
 * 줄거리는 기사 요약이 아니라 우리 만화의 이야기가 된다 (지침 3). RSS 구독과 기사로 만들기가 같은 각색을 쓴다.
 */
@Component
public class ArticleDialogue {

  /** 프롬프트 버전. 바꾸면 올린다 (제작 정보에 남는다). */
  public static final String VERSION = "article-v2";

  static final int MAX_TOKENS = 4000;

  /** 대사가 기사 문장을 이만큼 이어서 그대로 쓰면 베낀 것으로 본다. */
  static final int COPY_RUN = 40;

  static final String SYSTEM =
      """
      너는 뉴스 기사를 4컷 만화의 원작 대화로 각색하는 작가다. 네가 쓴 대화는 녹음한 실제 대화처럼 다음 단계(웃음·반전·진심이 담긴 장면 고르기, \
      콘티, 말풍선)에 그대로 들어간다.

      먼저 기사에서 사실(누가, 언제, 어디서, 무엇을, 왜, 숫자)만 뽑고, 기사 문장·표현·비유·제목·첫 문단(리드)·인용문은 버린다. \
      그다음 그 사실만 바탕으로 우리 만화의 이야기를 새로 지어낸다. 인물, 이야기 흐름(어디서 시작해 어떻게 끝나는지), 대사는 모두 네가 새로 만든다. \
      기사를 요약하거나 문장을 바꿔 쓰는(패러프레이즈) 것이 아니고, 기사의 서술 순서를 따라가지도 않는다.

      할 일: 기사 속 사건을 겪거나 지켜본 사람 2~4명이 그 일을 두고 실제로 나눌 법한 대화를 쓴다. \
      기사에 나온 사람(주민, 상인, 선수, 연구원, 공무원 등)이나 그 일을 곁에서 겪을 법한 사람(가족, 친구, 동료, 손님)을 고른다.

      규칙:
      1. 기사 문장을 그대로 옮기지 않는다. 대사에도 요약에도 기사 문장, 기사 속 인용문(따옴표 안의 말), 기자가 고른 독특한 표현·비유를 쓰지 않는다. \
      모든 대사는 사람이 입으로 하는 말투로 새로 쓴다. 기사 문장의 낱말 다섯 개 이상을 이어서 쓰지 않는다.
      2. 사실은 기사에 있는 것(누가, 언제, 어디서, 무엇을, 왜, 숫자)만 쓴다. 기사에 없는 사건·숫자·발언을 지어내지 않는다.
      3. 실존 인물의 실명은 쓰지 않고 역할(시장, 주민, 감독, 의사)로 부른다. 실존 인물이 하지 않은 말을 그 사람이 했다고 쓰지 않고, 실존 인물을 놀리거나 깎아내리지 않는다. \
      미성년자와 범죄·사고 피해자는 누구인지 알아볼 수 있게 쓰지 않는다(이름, 학교, 사는 곳, 나이와 동네를 함께 쓰지 않는다).
      4. 화자는 A, B, C, D만 쓴다. 각 화자의 첫 대사에서 그 사람이 누구인지 자연스럽게 드러나게 한다. \
      예) "나 이 시장에서 30년 떡집 했는데 이런 줄은 처음 본다."
      5. 20~40줄. 한 줄에 한두 문장. 사건이 시작되고, 커지고, 핵심(놀람·반전·감동)에 이르고, 마무리되는 흐름이 보이게 한다. \
      말다툼, 감탄, 농담, 걱정 같은 감정이 드러나는 순간을 살린다.
      6. 사망·재난·범죄 피해처럼 무거운 일은 피해자를 놀리거나 가볍게 다루지 않는다. 이런 기사는 위로·응원·교훈이 담긴 대화로 쓴다.
      7. 기사와 같은 언어로 쓴다(한국어 기사는 한국어). 광고·기자 이름·사진 설명·다른 기사 제목은 무시한다.
      """;

  static final String SCHEMA =
      """
      {"type":"object","properties":{
        "language":{"type":"string","enum":["ko","en"]},
        "lines":{"type":"array","items":{"type":"object","properties":{
          "speaker":{"type":"string","enum":["A","B","C","D"]},
          "text":{"type":"string"}},"required":["speaker","text"]}}},
       "required":["language","lines"]}
      """;

  private static final Set<String> SPEAKERS = Set.of("A", "B", "C", "D");

  /** 대사 한 줄. */
  public record Line(String speaker, String text) {}

  /** 각색한 대화. */
  public record Dialogue(String language, List<Line> lines) {}

  private final JsonMapper json;
  private final String model;
  private final int numCtx;

  ArticleDialogue(
      JsonMapper json,
      @Value("${scenario.llm-model:qwen3:8b}") String model,
      @Value("${scenario.num-ctx:16384}") int numCtx) {
    this.json = json;
    this.model = model;
    this.numCtx = numCtx;
  }

  /** LLM 작업 입력 (워커·외부 API 공통 모양: model, system, prompt, jsonSchema, options). */
  public ObjectNode payload(Article article) {
    ObjectNode payload = json.createObjectNode();
    payload.put("model", model);
    payload.put("system", SYSTEM.strip());
    payload.put("prompt", prompt(article));
    payload.set("jsonSchema", json.readTree(SCHEMA));
    ObjectNode options = payload.putObject("options");
    options.put("num_ctx", numCtx);
    options.put("temperature", 0.6);
    options.put("seed", (long) article.text().hashCode());
    options.put("num_predict", MAX_TOKENS);
    return payload;
  }

  /** 단계 기록에 남길 모델 정보. */
  public String modelInfo(Article article) {
    ObjectNode info = json.createObjectNode();
    info.put("model", model);
    info.put("promptVersion", VERSION);
    info.put("seed", (long) article.text().hashCode());
    return json.writeValueAsString(info);
  }

  static String prompt(Article article) {
    return "기사 제목: "
        + article.title()
        + "\n출처: "
        + article.site()
        + "\n\n기사 본문:\n"
        + article.text()
        + "\n\n위 기사에서 사실만 뽑아, 기사 문장과 표현은 쓰지 말고 규칙에 맞는 새 대화로 각색해 줘.";
  }

  /**
   * LLM 결과 {@code {content: "<JSON>"}}를 대화로 푼다.
   *
   * @throws IllegalArgumentException JSON이 아니거나, 대사가 너무 적거나, 기사 문장을 그대로 옮겼으면
   */
  public Dialogue parse(JsonNode result, Article article) {
    String content = result.path("content").asString("");
    int from = content.indexOf('{');
    int to = content.lastIndexOf('}');
    if (from < 0 || to < from) {
      throw new IllegalArgumentException("각색 결과가 JSON이 아니다");
    }
    JsonNode root = json.readTree(content.substring(from, to + 1));
    List<Line> lines = new ArrayList<>();
    for (JsonNode node : root.path("lines")) {
      String speaker = node.path("speaker").asString("").strip().toUpperCase(Locale.ROOT);
      String text = node.path("text").asString("").strip();
      if (!SPEAKERS.contains(speaker) || text.isEmpty()) {
        continue;
      }
      lines.add(new Line(speaker, text));
    }
    if (lines.size() < 6) {
      throw new IllegalArgumentException("각색한 대사가 너무 적다: " + lines.size());
    }
    String body = normalize(article.text());
    for (Line line : lines) {
      if (copied(normalize(line.text()), body)) {
        throw new IllegalArgumentException("대사가 기사 문장을 그대로 옮겼다");
      }
    }
    String language = root.path("language").asString("");
    if (!language.matches("ko|en")) {
      language = article.language() == null ? "ko" : article.language();
    }
    return new Dialogue(language, List.copyOf(lines));
  }

  /** 대사 속에 기사 본문과 똑같은 {@link #COPY_RUN}자 이상 구간이 있는지 (공백·문장 부호는 빼고 센다). */
  static boolean copied(String line, String body) {
    if (line.length() < COPY_RUN) {
      return false;
    }
    for (int i = 0; i + COPY_RUN <= line.length(); i += 5) {
      if (body.contains(line.substring(i, i + COPY_RUN))) {
        return true;
      }
    }
    return false;
  }

  private static String normalize(String text) {
    return text.replaceAll("[\\s\\p{P}\\p{S}]", "");
  }
}
