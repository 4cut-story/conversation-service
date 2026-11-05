package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.java21.fourcut.conversation.pipeline.GeminiProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class GeminiEmbedderTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final CharacterProperties properties =
      new CharacterProperties(true, 0.35, "gemini-embedding-001", "plus-face-sdxl-vit-h");

  private GeminiEmbedder embedder(String key) {
    return new GeminiEmbedder(
        RestClient.builder(),
        new GeminiProperties(key, "m", "http://localhost:1", null, null),
        properties,
        json);
  }

  @Test
  @DisplayName("REQ-018: 키가 없으면 임베딩하지 않는다")
  void disabled() {
    assertThat(embedder("").embed("red hair")).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 768차원 SEMANTIC_SIMILARITY로 요청한다")
  void request() {
    ObjectNode body = embedder("k").request(" red hair ");
    assertThat(body.path("model").asString()).isEqualTo("models/gemini-embedding-001");
    assertThat(body.path("taskType").asString()).isEqualTo("SEMANTIC_SIMILARITY");
    assertThat(body.path("outputDimensionality").asInt()).isEqualTo(768);
    assertThat(body.path("content").path("parts").path(0).path("text").asString())
        .isEqualTo("red hair");
  }

  @Test
  @DisplayName("REQ-018: 받은 임베딩은 길이 1로 맞춘다")
  void normalized() {
    ObjectNode response = json.createObjectNode();
    ArrayNode values = response.putObject("embedding").putArray("values");
    values.add(3.0).add(4.0);
    for (int i = 2; i < 768; i++) {
      values.add(0.0);
    }
    float[] v = GeminiEmbedder.values(response);
    assertThat(v[0]).isEqualTo(0.6f);
    assertThat(v[1]).isEqualTo(0.8f);
  }

  @Test
  @DisplayName("REQ-018: 차원이 다르면 실패한다")
  void wrongSize() {
    ObjectNode response = json.createObjectNode();
    response.putObject("embedding").putArray("values").add(1.0);
    assertThatThrownBy(() -> GeminiEmbedder.values(response))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("REQ-018: 호출이 실패하면 빈 값이다 (이름으로만 찾는다)")
  void failure() {
    assertThat(embedder("k").embed("red hair")).isEmpty();
  }
}
