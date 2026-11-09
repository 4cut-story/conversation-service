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
}
