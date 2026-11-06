package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.character.CharacterRef;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

class DrawReferencesTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final DrawHandler handler = new DrawHandler(null, null, null, json, null);

  private final ComicScript script =
      new ComicScript(
          "t",
          List.of(
              new ComicScript.Character("A", "민지", "", "girl, bob hair"),
              new ComicScript.Character("B", "도윤", "", "boy, glasses")),
          List.of(),
          true,
          List.of());

  private final CharacterRef ready =
      new CharacterRef(
          "aaaaaaaaaaaaaaaaaaaaa",
          "하늘",
          "red bob hair, round glasses",
          0.6,
          "characters/a/reference.png",
          "characters/a/ip-adapter/x.safetensors",
          "characters/a/ip-adapter/x.safetensors",
          0.1);
  private final CharacterRef fresh =
      new CharacterRef(
          "bbbbbbbbbbbbbbbbbbbbb",
          "해솔",
          "bearded man, blue hoodie",
          0.7,
          "characters/b/reference.png",
          null,
          "characters/b/ip-adapter/x.safetensors",
          0.2);

  @Test
  @DisplayName("REQ-018: 캐릭터를 찾은 인물은 캐릭터의 외모 문구로 그린다")
  void looks() {
    ComicScript drawn = DrawHandler.withLooks(script, Map.of("A", ready));
    assertThat(drawn.characters().get(0).look()).isEqualTo("red bob hair, round glasses");
    assertThat(drawn.characters().get(0).name()).isEqualTo("민지");
    assertThat(drawn.characters().get(1).look()).isEqualTo("boy, glasses");
    assertThat(DrawHandler.withLooks(script, Map.of())).isSameAs(script);
  }

  @Test
  @DisplayName("REQ-018: 컷의 인물은 장면에 처음 나온 순서이고, 예전 콘티는 등장인물 순서다")
  void people() {
    var panel = new ComicScript.Panel("기", "wide", "{B} looks at {A}, {B} laughs, {C} waits", null);
    assertThat(DrawHandler.people(script, panel)).containsExactly("B", "A");
    var legacy = new ComicScript.Panel("기", "wide", "two friends", null);
    assertThat(DrawHandler.people(script, legacy)).containsExactly("A", "B");
  }

  @Test
  @DisplayName("REQ-018: 혼자 나오는 컷은 전체, 두 명이면 장면 문장 순서와 관계없이 인물마다 정한 쪽에 선다")
  void regions() {
    var solo = new ComicScript.Panel("기", "close-up", "{A} eats", null);
    ArrayNode one = handler.references(script, solo, Map.of("A", ready));
    assertThat(one).hasSize(1);
    assertThat(one.get(0).path("region").asString()).isEqualTo("full");
    assertThat(one.get(0).path("embedGetKey").asString()).isEqualTo(ready.embedKey());
    assertThat(one.get(0).path("scale").asDouble()).isEqualTo(0.6);

    var duo = new ComicScript.Panel("승", "medium", "{B} hands {A} a cup", null);
    ArrayNode two = handler.references(script, duo, Map.of("A", ready, "B", fresh));
    assertThat(two).hasSize(2);
    assertThat(two.get(0).path("character").asString()).isEqualTo(ready.publicId());
    assertThat(two.get(0).path("region").asString()).isEqualTo("left");
    assertThat(two.get(1).path("character").asString()).isEqualTo(fresh.publicId());
    assertThat(two.get(1).path("region").asString()).isEqualTo("right");
  }

  @Test
  @DisplayName("REQ-018: 세 명이 나오는 컷은 고정 자리 두 명만 참조를 넣고 셋째 인물은 넣지 않는다")
  void three() {
    ComicScript trio =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "민지", "", "girl"),
                new ComicScript.Character("B", "도윤", "", "boy"),
                new ComicScript.Character("C", "하준", "", "old man")),
            List.of(),
            true,
            List.of());
    var panel = new ComicScript.Panel("전", "wide", "{C} shouts at {B} and {A}", null);
    CharacterRef third =
        new CharacterRef("ccccccccccccccccccccc", "할배", "old man", 0.6, "r", "e", "e", 0.1);
    ArrayNode refs = handler.references(trio, panel, Map.of("A", ready, "B", fresh, "C", third));
    assertThat(refs).hasSize(2);
    assertThat(refs.get(0).path("region").asString()).isEqualTo("left");
    assertThat(refs.get(0).path("character").asString()).isEqualTo(ready.publicId());
    assertThat(refs.get(1).path("region").asString()).isEqualTo("right");
  }

  @Test
  @DisplayName("REQ-018: embedding이 없으면 참조 그림과 embedding을 올릴 키를 넘긴다")
  void notReady() {
    var duo = new ComicScript.Panel("승", "medium", "{B} and {A} talk", null);
    ArrayNode refs = handler.references(script, duo, Map.of("B", fresh));
    assertThat(refs).hasSize(1);
    JsonNode r = refs.get(0);
    assertThat(r.path("region").asString()).isEqualTo("right");
    assertThat(r.has("embedGetKey")).isFalse();
    assertThat(r.path("imageGetKey").asString()).isEqualTo(fresh.referenceImageKey());
    assertThat(r.path("embedUploadPutKey").asString()).isEqualTo(fresh.embedTargetKey());
  }

  @Test
  @DisplayName("REQ-018: 캐릭터가 없는 컷에는 참조를 넣지 않는다")
  void none() {
    var panel = new ComicScript.Panel("결", "wide", "{A} waves", null);
    assertThat(handler.references(script, panel, Map.of())).isEmpty();
    assertThat(handler.references(script, panel, Map.of("B", fresh))).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 결과를 받으면 이번 작업에서 계산한 embedding의 캐릭터를 고른다")
  void computed() {
    JsonNode payload =
        json.readTree(
            "{\"images\":[{\"references\":[{\"character\":\"x\",\"embedUploadPutKey\":\"k\"},"
                + "{\"character\":\"y\",\"embedGetKey\":\"k2\"}]},{\"name\":\"panel-2\"}]}");
    assertThat(DrawHandler.computedEmbeds(payload)).containsExactly("x");
    assertThat(DrawHandler.computedEmbeds(null)).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 캐릭터를 찾은 인물은 가명 대신 캐릭터 이름을 쓰고, 못 찾은 인물은 가명을 둔다")
  void names() {
    ComicScript named = DrawHandler.withNames(script, Map.of("A", ready));
    assertThat(named.characters().get(0).name()).isEqualTo("하늘");
    assertThat(named.characters().get(1).name()).isEqualTo("도윤");
    assertThat(named.characters().get(0).lookId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaa");
    assertThat(named.characters().get(1).lookId()).isNull();
    assertThat(DrawHandler.withNames(script, Map.of())).isSameAs(script);
  }

  @Test
  @DisplayName("REQ-018: 참조가 있는 컷에는 설정의 IP-Adapter 강도 스케줄을 함께 보낸다")
  void ipSchedule() {
    JsonNode s = handler.ipSchedule(new ComicProperties(768, "s", "n", "http://x", 0.25, 3, 0.15));
    assertThat(s.path("earlyScale").asDouble()).isEqualTo(0.25);
    assertThat(s.path("earlySteps").asInt()).isEqualTo(3);
    assertThat(s.path("lateBoost").asDouble()).isEqualTo(0.15);
  }

  @Test
  @DisplayName("REQ-018: 컷 인물이 모두 캐릭터 참조면 컷마다 시드를 바꾸고, 참조 없는 인물이 있으면 네 컷이 같은 시드다")
  void panelSeed() {
    Map<String, Staging.Side> duo = Map.of("A", Staging.Side.LEFT, "B", Staging.Side.RIGHT);
    Map<String, CharacterRef> both = Map.of("A", ready, "B", fresh);
    long s1 = DrawHandler.panelSeed(100, 0, duo, both);
    long s2 = DrawHandler.panelSeed(100, 1, duo, both);
    assertThat(s1).isNotEqualTo(100).isNotEqualTo(s2);
    assertThat(DrawHandler.panelSeed(100, 1, duo, Map.of("A", ready))).isEqualTo(100);
    assertThat(DrawHandler.panelSeed(100, 2, Map.of(), both)).isEqualTo(100);
  }
}
