package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ScriptDraftTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  @DisplayName("FR-006: 콘티 초안의 아래옷·신발 칸은 외모 뒤에 붙고 소품 자리표는 모든 컷에 같은 정의로 바뀐다")
  void flatten() {
    JsonNode draft =
        json.readTree(
            """
            {"title":"목검 대결","characters":[{"speaker":"A","look":"boy, blue robe","bottom":"white trousers",
             "shoes":" ","role":"동생"}],
             "props":[{"key":"목검","draw":"light brown wooden practice sword"},{"key":"B","draw":"x"}],
             "panels":[{"scene":"{A} swings {목검}, dojo","setting":"dojo, {목검} rack"},
                       {"scene":"{A} drops { 목검 }, {B} laughs, {공}","setting":"dojo"}]}
            """);
    JsonNode out = ScriptDraft.flatten(draft);
    assertThat(out.path("characters").get(0).path("look").asString())
        .isEqualTo("boy, blue robe, white trousers");
    assertThat(out.path("characters").get(0).has("bottom")).isFalse();
    assertThat(out.has("props")).isFalse();
    assertThat(out.path("panels").get(0).path("scene").asString())
        .isEqualTo("{A} swings light brown wooden practice sword, dojo");
    assertThat(out.path("panels").get(0).path("setting").asString())
        .isEqualTo("dojo, light brown wooden practice sword rack");
    assertThat(out.path("panels").get(1).path("scene").asString())
        .isEqualTo("{A} drops light brown wooden practice sword, {B} laughs, 공");
    assertThat(draft.path("props").isArray()).isTrue();
  }

  @Test
  @DisplayName("FR-006: 컷의 action·장소 key·시선은 장면 문장, 장소 정의가 앞선 배경, 시선 붙은 표정으로 펼친다")
  void structuredPanel() {
    JsonNode draft =
        json.readTree(
            """
            {"characters":[{"speaker":"A","look":"girl","bottom":"dark pants","shoes":"red boots"}],
             "props":[{"key":"휴대폰","draw":"phone with glowing green screen, no text"}],
             "places":[{"key":"텃밭","draw":"small backyard vegetable garden, wooden fence"}],
             "panels":[
              {"action":"{A} holds up {휴대폰}","place":"텃밭","setting":"night",
               "faces":[{"speaker":"A","face":"wide-eyed","lookAt":"{휴대폰}"},
                        {"speaker":"B","face":"grin","lookAt":"{A}"}]},
              {"action":"{A} and {B} wave","place":"없는 곳","setting":"",
               "faces":[{"speaker":"A","face":"laughing","lookAt":"{B}"},
                        {"speaker":"B","face":"smile","lookAt":"viewer"}]},
              {"action":"{A} sits","place":"bus stop","setting":"rain",
               "faces":[{"speaker":"A","face":"","lookAt":"sky"},{"speaker":"A","face":"x","lookAt":"down"},
                        {"speaker":"A","face":"y","lookAt":"looking away"},{"speaker":"A","face":"z","lookAt":""}]}]}
            """);
    JsonNode panels = ScriptDraft.flatten(draft).path("panels");
    assertThat(panels.get(0).path("scene").asString())
        .isEqualTo(
            "{A} holds up phone with glowing green screen, no text, small backyard vegetable garden");
    assertThat(panels.get(0).path("setting").asString())
        .isEqualTo("small backyard vegetable garden, wooden fence, night");
    assertThat(panels.get(0).has("action")).isFalse();
    assertThat(panels.get(0).path("faces").get(0).path("face").asString())
        .isEqualTo("wide-eyed, looking at it");
    assertThat(panels.get(0).path("faces").get(1).path("face").asString())
        .isEqualTo("grin, looking at the other person");
    assertThat(panels.get(0).path("faces").get(1).has("lookAt")).isFalse();
    assertThat(panels.get(1).path("setting").asString())
        .isEqualTo("small backyard vegetable garden, wooden fence");
    assertThat(panels.get(1).path("faces").get(1).path("face").asString())
        .isEqualTo("smile, looking at viewer");
    assertThat(panels.get(2).path("scene").asString()).isEqualTo("{A} sits, bus stop");
    assertThat(panels.get(2).path("setting").asString()).isEqualTo("bus stop, rain");
    JsonNode faces = panels.get(2).path("faces");
    assertThat(faces.get(0).path("face").asString()).isEqualTo("looking up at the sky");
    assertThat(faces.get(1).path("face").asString()).isEqualTo("x, looking down");
    assertThat(faces.get(2).path("face").asString()).isEqualTo("y, looking away");
    assertThat(faces.get(3).path("face").asString()).isEqualTo("z");
    assertThat(ScriptDraft.gaze("{C}", "{A} sits", Map.of()))
        .isEqualTo("looking off to the side at someone");
  }

  @Test
  @DisplayName("FR-006: 소품 시선은 draw 앞의 관사와 a single을 빼고 with 앞 물건 이름까지만 쓴다")
  void gazeSingle() {
    assertThat(
            ScriptDraft.gaze(
                "{오두막}",
                "{A} stands",
                Map.of("오두막", "a single small wooden hut with glowing window")))
        .isEqualTo("looking at the small wooden hut");
  }

  @Test
  @DisplayName("FR-006: 앞을 보는 시선은 \"looking at forward\"가 아니라 \"looking straight ahead\"로 쓴다")
  void gazeForward() {
    assertThat(ScriptDraft.gaze("forward", "{A} knocks", Map.of()))
        .isEqualTo("looking straight ahead");
    assertThat(ScriptDraft.gaze("ahead", "{A} knocks", Map.of()))
        .isEqualTo("looking straight ahead");
    assertThat(ScriptDraft.gaze("camera", "{A} knocks", Map.of())).isEqualTo("looking at viewer");
  }

  @Test
  @DisplayName("FR-006: 칸을 나누지 않은 예전 콘티 응답은 그대로 둔다")
  void legacy() {
    JsonNode old =
        json.readTree(
            """
            {"characters":[{"speaker":"A","look":"girl, red dress"}],"panels":[{"scene":"{A} waves, park"}]}
            """);
    assertThat(ScriptDraft.flatten(old)).isEqualTo(old);
    assertThat(ScriptDraft.flatten(json.readTree("[1]")).isArray()).isTrue();
    assertThat(ScriptDraft.resolve("{A} and {B}", Map.of())).isEqualTo("{A} and {B}");
  }

  @Test
  @DisplayName("FR-006: 소품 정의 안의 다른 소품 자리표도 그 정의로 바꾼다")
  void nestedProp() {
    Map<String, String> props =
        ScriptDraft.props(
            json.readTree(
                """
                [{"key":"강아지집","draw":"small wooden doghouse"},
                 {"key":"양말더미","draw":"heap of socks spilling out of {강아지집}"}]
                """));
    assertThat(props.get("양말더미")).isEqualTo("heap of socks spilling out of small wooden doghouse");
  }
}
