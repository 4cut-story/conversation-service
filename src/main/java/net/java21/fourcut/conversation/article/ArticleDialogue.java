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
  public static final String VERSION = "article-v5";

  static final int MAX_TOKENS = 4000;

  /** 대사가 기사 문장을 이만큼 이어서 그대로 쓰면 베낀 것으로 본다. */
  static final int COPY_RUN = 40;

  static final String SYSTEM =
      """
      너는 뉴스 기사를 4컷 만화의 원작 대화로 각색하는 작가다. 네가 쓴 대화는 녹음한 실제 대화처럼 다음 단계(장면 고르기, \
      콘티, 말풍선)에 그대로 들어간다. 만화를 본 사람이 원래 기사를 읽으면 "아, 그 기사구나" 하고 바로 짝지을 수 있어야 한다.

      먼저 기획(plan)을 쓰고, 그 기획대로 대화(lines)를 쓴다.

      기획 (plan)
      - points: 기사의 핵심 사실 3~5개. 누가(역할로), 어디서, 무엇을 했고, 왜, 결과와 숫자는 얼마인지를 한 줄씩 쉬운 말로 쓴다. \
      기사를 읽은 사람이 가장 먼저 떠올릴 사실부터 쓴다. 실명·기업명은 역할·일반 표현으로 쓴다.
      - angle: 이 기사를 어떤 흐름으로 살릴지 짧은 이름(2~12자). 정해진 목록은 없다. 기사가 무엇을 말하는지 보고 가장 어울리는 \
      흐름을 네가 정한다. 참고할 예(이것만 있는 것이 아니다):
        · 누군가 잘한 일·선행·성과 → 칭찬: 그 사람이 무엇을 잘했는지 포인트를 콕 집어 띄워 준다.
        · 미담·사연 → 감동: 마음이 움직이는 순간을 더 크게, 천천히 보여 준다.
        · 비리·갑질·부정·탁상행정·안전 불감증·책임 떠넘기기처럼 사회적으로 잘못된 일 → 풍자(필수, 아래 "풍자" 참고).
        · 사고·재난 → 위로와 "다음엔 이렇게 조심하자"는 안전 메시지.
        · 물가·경제 → 공감과 허탈한 웃음.
        · 새 정책·제도 → "그래서 나한테 뭐가 바뀌는데?"를 쉽게 풀어 주기.
        · 과학·기술·발견 → 놀라움과 호기심.
        · 스포츠·도전 → 응원과 함께 기뻐하기.
        · 찬반이 갈리는 일 → 양쪽 입장을 맞세워 보여 주기.
        · 계속 되풀이되는 문제 → 블랙코미디.
        · 날씨·계절·생활 정보·동네 소식 → "맞아, 나도 그래" 하는 공감과 생활 속 웃음, 끝에 쓸모 있는 한마디.
      - message: 만화를 다 본 사람에게 남길 한 줄(누구를 칭찬하는지, 무엇을 꼬집는지, 무엇을 조심하자는지 등).
      - beats: 기, 승, 전, 결 4개. angle에 맞춰 각 단계에서 무슨 일이 벌어지는지 한 줄씩. 전은 angle이 가장 크게 드러나는 순간이고 \
      points 중 가장 중요한 사실이 터지는 자리다.

      풍자
      - 사회적으로 잘못된 일을 다루는 기사는 반드시 풍자로 메시지를 전한다. 그냥 화내거나 슬퍼하는 대화, 남 일처럼 전하는 대화로 쓰지 않는다.
      - 꼬집는 대상은 잘못한 행동과 그것을 낳은 구조·관행이다. 반어("참 대단한 대책이네"), 과장(말도 안 되게 부풀린 비유), \
      말과 행동이 어긋나는 상황, 엉뚱한 핑계가 들통나는 반전으로 웃기면서 무엇이 잘못인지 분명히 보이게 한다.
      - 피해자·약자·평범한 사람을 웃음거리로 삼지 않는다. 잘못한 쪽도 역할(한 회사 대표, 담당 공무원, 윗선)로만 부르고, \
      기사에 있는 잘못만 꼬집는다. 기사에 없는 잘못이나 혐의를 지어내지 않는다.

      대화 (lines)
      - 기사 속 사건을 겪거나 지켜본 사람 2~4명이 그 일이 벌어지는 곳에서 그 일을 겪으며 실제로 나눌 법한 대화를 쓴다. \
      기사에 나온 사람(주민, 상인, 선수, 연구원, 공무원 등)이나 그 일을 곁에서 겪을 법한 사람(가족, 친구, 동료, 손님)을 고른다. \
      대화 자리는 그 일이 벌어진 곳이나 그 일이 닿는 현장(시장, 법정, 경기장, 공장, 학교, 병원, 논밭, 바닷가, 역, 관공서 창구 등)이다. \
      집·거실·카페·식탁에서 뉴스를 보거나 전해 들으며 이야기하는 대화로 쓰지 않는다. 앞의 몇 줄에서 지금 어디에 있는지가 말로 자연스럽게 드러나게 한다. \
      예) "와, 법정에 사람 진짜 많다. 방청석 꽉 찼네."
      - beats 순서대로 흘러가게 쓴다. points는 하나도 빠짐없이 대사에 나와야 한다. 숫자·결과·무슨 일인지는 기사 그대로 정확히 말하고, \
      말투만 사람 입말로 바꾼다. 예) points "한 마을 주민 20명이 3년 동안 쓰레기를 주워 바닷가를 되살렸다" → \
      "우리 스무 명이 3년을 매주 주웠잖아. 그래서 이 모래사장이 돌아온 거야." 핵심과 상관없는 잡담으로 줄을 채우지 않는다.

      규칙:
      1. 기사 문장을 그대로 옮기지 않는다. 대사에도 기획에도 기사 문장, 기사 속 인용문(따옴표 안의 말), 기자가 고른 독특한 표현·비유를 쓰지 않는다. \
      모든 대사는 사람이 입으로 하는 말투로 새로 쓴다. 기사 문장의 낱말 다섯 개 이상을 이어서 쓰지 않는다. 사실(숫자, 무슨 일)은 바꾸지 않고 말투만 바꾼다.
      2. 사실은 기사에 있는 것(누가, 언제, 어디서, 무엇을, 왜, 숫자)만 쓴다. 기사에 없는 사건·숫자·발언을 지어내지 않는다.
      3. 실존 인물의 실명은 쓰지 않고 역할(시장, 주민, 감독, 의사)로 부른다. 실존 인물이 하지 않은 말을 그 사람이 했다고 쓰지 않는다. \
      실존 기업·브랜드·정당·단체·학교 이름도 쓰지 않고 일반 표현(대기업, 한 IT 회사, 카페, 한 정당, 한 대학)으로 바꾼다. \
      소속·지역과 직함을 붙여 한 사람을 알아볼 수 있는 말(OO그룹 회장·총수, OO당 대표, OO시장, OO구단 감독)도 쓰지 않고 \
      직함만 남기거나 일반 표현(회장님, 한 정당 대표, 시장님, 감독)으로 쓴다. 정치인·연예인·운동선수·기업인처럼 이름이 알려진 사람도 마찬가지다. \
      미성년자와 범죄·사고 피해자는 누구인지 알아볼 수 있게 쓰지 않는다(이름, 학교, 사는 곳, 나이와 동네를 함께 쓰지 않는다).
      4. 화자는 A, B, C, D만 쓴다. 각 화자의 첫 대사에서 그 사람이 누구인지 자연스럽게 드러나게 한다. \
      예) "나 이 시장에서 30년 떡집 했는데 이런 줄은 처음 본다."
      5. 20~40줄. 한 줄에 한두 문장. 말다툼, 감탄, 농담, 걱정 같은 감정이 드러나는 순간을 살린다.
      6. 사망·재난·범죄 피해처럼 무거운 일은 피해자를 놀리거나 가볍게 다루지 않는다. 피해자에게는 위로·응원을 건넨다. \
      그 피해가 누군가의 잘못(안전 수칙 무시, 부실 공사, 늑장 대응)에서 왔으면 그 잘못은 풍자로 꼬집는다.
      7. 기사와 같은 언어로 쓴다(한국어 기사는 한국어). 광고·기자 이름·사진 설명·다른 기사 제목은 무시한다.
      """;

  static final String SCHEMA =
      """
      {"type":"object","properties":{
        "plan":{"type":"object","properties":{
          "points":{"type":"array","minItems":3,"maxItems":5,"items":{"type":"string"}},
          "angle":{"type":"string"},
          "message":{"type":"string"},
          "beats":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"string"}}},
         "required":["points","angle","message","beats"]},
        "language":{"type":"string","enum":["ko","en"]},
        "lines":{"type":"array","items":{"type":"object","properties":{
          "speaker":{"type":"string","enum":["A","B","C","D"]},
          "text":{"type":"string"}},"required":["speaker","text"]}}},
       "required":["plan","language","lines"]}
      """;

  private static final Set<String> SPEAKERS = Set.of("A", "B", "C", "D");

  /** 대사 한 줄. */
  public record Line(String speaker, String text) {}

  /**
   * 각색 기획. 장면 고르기와 콘티가 이 흐름과 핵심 사실을 이어 받아, 만화가 기사의 요점을 지키게 한다.
   *
   * @param angle 기사를 살릴 흐름 이름 (칭찬, 감동, 풍자 등. 정해진 목록은 없다)
   * @param message 만화를 본 사람에게 남길 한 줄
   * @param beats 기·승·전·결에 벌어질 일
   * @param points 대사에 꼭 남길 기사 핵심 사실
   */
  public record Plan(String angle, String message, List<String> beats, List<String> points) {

    /** 장면 고르기·콘티 입력에 붙일 글. */
    public String brief() {
      StringBuilder out = new StringBuilder();
      out.append("기사 흐름: ").append(angle);
      if (message != null && !message.isBlank()) {
        out.append(" — ").append(message);
      }
      if (!beats.isEmpty()) {
        out.append("\n흐름 단계: ").append(String.join(" / ", beats));
      }
      out.append("\n기사 핵심 (대사에 꼭 남긴다):");
      for (String point : points) {
        out.append("\n- ").append(point);
      }
      return out.toString();
    }
  }

  /**
   * 각색한 대화.
   *
   * @param plan 각색 기획. 모델이 기획을 내지 않았으면 null
   */
  public record Dialogue(String language, List<Line> lines, Plan plan) {

    public Dialogue(String language, List<Line> lines) {
      this(language, lines, null);
    }
  }

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
        + "\n\n위 기사의 핵심 사실과 어울리는 흐름을 먼저 기획하고, 기사 문장과 표현은 쓰지 말고 그 기획대로 새 대화로 각색해 줘.";
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
    return new Dialogue(language, List.copyOf(lines), plan(root.path("plan")));
  }

  /** 기획을 푼다. 흐름 이름이나 핵심 사실이 없으면 null (각색은 그대로 쓴다). */
  static Plan plan(JsonNode node) {
    String angle = node.path("angle").asString("").strip();
    List<String> points = texts(node.path("points"));
    if (angle.isEmpty() || points.isEmpty()) {
      return null;
    }
    return new Plan(
        angle, node.path("message").asString("").strip(), texts(node.path("beats")), points);
  }

  private static List<String> texts(JsonNode array) {
    List<String> out = new ArrayList<>();
    for (JsonNode item : array) {
      String text = item.asString("").strip();
      if (!text.isEmpty()) {
        out.add(text);
      }
    }
    return List.copyOf(out);
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
