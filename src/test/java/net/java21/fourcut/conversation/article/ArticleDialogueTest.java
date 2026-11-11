package net.java21.fourcut.conversation.article;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ArticleDialogueTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final ArticleDialogue dialogue = new ArticleDialogue(json, "qwen3:8b", 16384);

  private final Article article =
      new Article(
          "https://news.example.com/a",
          "30년 떡집의 마지막 날",
          "예시신문",
          "ko",
          ArticleExtractorTest.PARAGRAPH + "\n주인 할머니는 남은 떡을 모두 나눠 주었다.");

  private ObjectNode result(String... lines) {
    ObjectNode content = json.createObjectNode();
    content.put("language", "ko");
    ArrayNode list = content.putArray("lines");
    for (int i = 0; i < lines.length; i++) {
      list.addObject().put("speaker", i % 2 == 0 ? "A" : "b").put("text", lines[i]);
    }
    ObjectNode result = json.createObjectNode();
    result.put("content", "답: " + json.writeValueAsString(content) + " 끝");
    return result;
  }

  @Test
  @DisplayName("기사로 만들기: LLM 작업에 기사 제목·출처·본문과 베끼지 말라는 규칙을 넣는다")
  void payload() {
    ObjectNode payload = dialogue.payload(article);
    assertThat(payload.path("model").asString()).isEqualTo("qwen3:8b");
    assertThat(payload.path("system").asString()).contains("그대로 옮기지 않는다");
    assertThat(payload.path("prompt").asString())
        .contains("30년 떡집의 마지막 날")
        .contains("예시신문")
        .contains("남은 떡");
    assertThat(payload.path("jsonSchema").path("required").toString()).contains("lines");
    assertThat(payload.path("options").path("num_predict").asInt())
        .isEqualTo(ArticleDialogue.MAX_TOKENS);
    assertThat(dialogue.modelInfo(article)).contains(ArticleDialogue.VERSION);
  }

  @Test
  @DisplayName("기사로 만들기: 각색한 대화를 화자와 대사로 푼다")
  void parse() {
    ArticleDialogue.Dialogue parsed =
        dialogue.parse(
            result(
                "할머니, 진짜 문 닫으세요?",
                "그래, 30년 했으면 됐지.",
                "아침부터 줄 선 거 보셨어요?",
                "다들 고맙지 뭐.",
                "손주가 카페 한다면서요?",
                "응, 또 놀러 와."),
            article);
    assertThat(parsed.language()).isEqualTo("ko");
    assertThat(parsed.lines()).hasSize(6);
    assertThat(parsed.lines().get(1).speaker()).isEqualTo("B");
  }

  @Test
  @DisplayName("기사로 만들기: 대사가 너무 적거나 JSON이 아니면 다시 각색한다")
  void tooFew() {
    assertThatThrownBy(() -> dialogue.parse(result("하나", "둘"), article))
        .isInstanceOf(IllegalArgumentException.class);
    ObjectNode broken = json.createObjectNode().put("content", "몰라요");
    assertThatThrownBy(() -> dialogue.parse(broken, article))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("기사로 만들기: 대사가 기사 문장을 그대로 옮기면 받지 않는다 (저작권)")
  void copied() {
    assertThatThrownBy(
            () ->
                dialogue.parse(
                    result(
                        "있잖아, " + ArticleExtractorTest.PARAGRAPH,
                        "그래?",
                        "응.",
                        "놀랍다.",
                        "가 보자.",
                        "좋아."),
                    article))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("그대로");
    assertThat(ArticleDialogue.copied("짧은말", "짧은말")).isFalse();
  }

  @Test
  @DisplayName("응답 언어가 틀리면 기사 언어를 쓴다")
  void languageFallback() {
    ObjectNode content = json.createObjectNode();
    content.put("language", "jp");
    ArrayNode list = content.putArray("lines");
    for (int i = 0; i < 6; i++) {
      list.addObject().put("speaker", "C").put("text", "대사 " + i);
    }
    ObjectNode result = json.createObjectNode().put("content", json.writeValueAsString(content));
    assertThat(dialogue.parse(result, article).language()).isEqualTo("ko");
    Article noLang = new Article("u", "t", "s", null, article.text());
    assertThat(dialogue.parse(result, noLang).language()).isEqualTo("ko");
  }

  @Test
  @DisplayName("기사로 만들기: 흐름은 정해진 목록 없이 기사마다 정하고, 사회적 잘못은 풍자로 쓰게 한다")
  void planRules() {
    String system = dialogue.payload(article).path("system").asString();
    assertThat(system).contains("정해진 목록은 없다").contains("반드시 풍자로").contains("points는 하나도 빠짐없이");
    String schema = dialogue.payload(article).path("jsonSchema").toString();
    assertThat(schema).contains("plan").contains("points").contains("beats");
  }

  @Test
  @DisplayName("기사로 만들기: 각색 기획(흐름, 핵심 사실)을 함께 풀어 다음 단계에 넘길 글로 만든다")
  void plan() {
    ObjectNode result = result("가", "나", "다", "라", "마", "바");
    ObjectNode content = (ObjectNode) json.readTree(contentOf(result));
    ObjectNode plan = content.putObject("plan");
    plan.put("angle", "감동");
    plan.put("message", "떡집 할머니의 30년에 고마움을");
    plan.putArray("beats").add("마지막 날 아침 긴 줄").add("단골들의 사연").add("남은 떡을 다 나눔").add("박수");
    plan.putArray("points").add("30년 된 떡집이 문을 닫았다").add(" ").add("주인 할머니가 남은 떡을 모두 나눠 주었다");
    result.put("content", json.writeValueAsString(content));

    ArticleDialogue.Plan parsed = dialogue.parse(result, article).plan();
    assertThat(parsed.angle()).isEqualTo("감동");
    assertThat(parsed.points()).hasSize(2);
    assertThat(parsed.brief())
        .contains("기사 흐름: 감동 — 떡집 할머니의 30년에 고마움을")
        .contains("흐름 단계: 마지막 날 아침 긴 줄 / 단골들의 사연")
        .contains("기사 핵심 (대사에 꼭 남긴다):\n- 30년 된 떡집이 문을 닫았다");
  }

  @Test
  @DisplayName("기사로 만들기: 기획이 없거나 비면 대화만 쓴다")
  void noPlan() {
    assertThat(dialogue.parse(result("가", "나", "다", "라", "마", "바"), article).plan()).isNull();
    assertThat(ArticleDialogue.plan(json.readTree("{\"angle\":\"풍자\",\"points\":[]}"))).isNull();
    ArticleDialogue.Plan bare =
        ArticleDialogue.plan(json.readTree("{\"angle\":\"풍자\",\"points\":[\"핵심\"]}"));
    assertThat(bare.brief()).isEqualTo("기사 흐름: 풍자\n기사 핵심 (대사에 꼭 남긴다):\n- 핵심");
  }

  private String contentOf(ObjectNode result) {
    String content = result.path("content").asString();
    return content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1);
  }
}
