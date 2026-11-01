package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.stt.Transcript.Utterance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class SceneTimesTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final List<Utterance> LINES =
      List.of(
          new Utterance("A", 240.5, 250, "택배 왔어"),
          new Utterance("B", 251.2, 258, "그거 입영통지서 아니야?"),
          new Utterance("A", 261, 270, "진짜네"),
          new Utterance("B", 281, 300, "12월 4일 논산 훈련소"),
          new Utterance("A", 301, 500, "엄마 눈에는 아직 아기야"));

  private static ObjectNode candidate(String from, String to) {
    ObjectNode n = JSON.createObjectNode();
    n.put("kind", "IMPORTANT");
    n.put("from", from);
    n.put("to", to);
    return n;
  }

  @Test
  @DisplayName("FR-004: [분:초] 표시를 초로 바꾼다")
  void seconds() {
    assertThat(SceneTimes.seconds("04:11")).isEqualTo(251);
    assertThat(SceneTimes.seconds("101:05")).isEqualTo(6065);
    assertThat(SceneTimes.seconds("4:70")).isEqualTo(-1);
    assertThat(SceneTimes.seconds("40")).isEqualTo(-1);
    assertThat(SceneTimes.seconds("aa:bb")).isEqualTo(-1);
    assertThat(SceneTimes.seconds(null)).isEqualTo(-1);
  }

  @Test
  @DisplayName("FR-004: 장면 구간은 LLM이 고른 줄의 실제 발화 시각으로 정하고 30초보다 짧으면 뒤 발화를 더한다")
  void resolve() {
    JsonNode r = SceneTimes.resolve(candidate("04:11", "04:21"), LINES);
    assertThat(r.path("start").asDouble()).isEqualTo(251.2);
    assertThat(r.path("end").asDouble()).isEqualTo(300.0);
    assertThat(r.has("from")).isFalse();

    JsonNode longScene = SceneTimes.resolve(candidate("04:00", "05:01"), LINES);
    assertThat(longScene.path("start").asDouble()).isEqualTo(240.5);
    assertThat(longScene.path("end").asDouble()).isEqualTo(420.5);

    JsonNode inside = SceneTimes.resolve(candidate("04:05", "04:41"), LINES);
    assertThat(inside.path("start").asDouble()).isEqualTo(240.5);
  }

  @Test
  @DisplayName("FR-004: 없는 줄이나 거꾸로 된 구간은 빈 구간으로 만들어 후보에서 빠진다")
  void invalid() {
    assertThat(SceneTimes.resolve(candidate("09:00", "09:10"), LINES).path("end").asDouble())
        .isZero();
    assertThat(SceneTimes.resolve(candidate("04:41", "04:11"), LINES).path("end").asDouble())
        .isZero();
    ObjectNode old = JSON.createObjectNode().put("start", 10).put("end", 50);
    assertThat(SceneTimes.resolve(old, LINES)).isSameAs(old);
  }
}
