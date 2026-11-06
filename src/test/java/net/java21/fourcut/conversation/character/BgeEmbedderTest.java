package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class BgeEmbedderTest {

  private final JsonMapper json = JsonMapper.builder().build();

  private BgeEmbedder embedder(String url) {
    return new BgeEmbedder(
        RestClient.builder(),
        new CharacterProperties(true, 0.35, url, "bge-m3", "plus-face-sdxl-vit-h"),
        json);
  }

  @Test
  @DisplayName("REQ-018: 임베딩 서버 주소가 없으면 임베딩하지 않는다")
  void disabled() {
    assertThat(embedder("").embed("red hair")).isEmpty();
    assertThat(embedder("http://localhost:1").embed(" ")).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: OpenAI 호환 형식(model, input)으로 요청한다")
  void request() {
    BgeEmbedder e = embedder("http://localhost:1");
    ObjectNode body = e.request(List.of(" red hair ", "old man"));
    assertThat(body.path("model").asString()).isEqualTo("bge-m3");
    assertThat(body.path("input").path(0).asString()).isEqualTo("red hair");
    assertThat(body.path("input").size()).isEqualTo(2);
    assertThat(e.model()).isEqualTo("bge-m3");
  }

  @Test
  @DisplayName("REQ-018: 받은 1024차원 임베딩은 index 순서로 읽고 길이 1로 맞춘다")
  void normalized() {
    ObjectNode response = json.createObjectNode();
    ArrayNode data = response.putArray("data");
    item(data, 1, 0.0, 2.0, 1024);
    item(data, 0, 3.0, 4.0, 1024);
    List<float[]> v = BgeEmbedder.values(response, 2);
    assertThat(v.get(0)).hasSize(1024);
    assertThat(v.get(0)[0]).isEqualTo(0.6f);
    assertThat(v.get(0)[1]).isEqualTo(0.8f);
    assertThat(v.get(1)[1]).isEqualTo(1f);
  }

  @Test
  @DisplayName("REQ-018: 개수나 차원이 다르거나 0이면 실패한다")
  void invalid() {
    ObjectNode response = json.createObjectNode();
    item(response.putArray("data"), 0, 1.0, 0.0, 768);
    assertThatThrownBy(() -> BgeEmbedder.values(response, 1))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> BgeEmbedder.values(response, 2))
        .isInstanceOf(IllegalStateException.class);
    ObjectNode zero = json.createObjectNode();
    item(zero.putArray("data"), 0, 0.0, 0.0, 1024);
    assertThatThrownBy(() -> BgeEmbedder.values(zero, 1)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("REQ-018: 빈 글이 섞이면 묶음 임베딩을 하지 않는다")
  void blankInBatch() {
    assertThat(embedder("http://localhost:1").embedAll(List.of("a", " "))).isEmpty();
    assertThat(embedder("http://localhost:1").embedAll(List.of())).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 호출이 실패하면 빈 값이다 (이름으로만 찾는다)")
  void failure() {
    assertThat(embedder("http://localhost:1").embed("red hair")).isEmpty();
  }

  private static void item(ArrayNode data, int index, double x, double y, int size) {
    ObjectNode item = data.addObject();
    item.put("index", index);
    ArrayNode values = item.putArray("embedding");
    values.add(x).add(y);
    for (int i = 2; i < size; i++) {
      values.add(0.0);
    }
  }
}
