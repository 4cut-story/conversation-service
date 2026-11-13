package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class LivePeopleTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final PanelPlacement placement = mock(PanelPlacement.class);

  private static final ComicScript SCRIPT =
      new ComicScript(
          "t",
          List.of(
              new ComicScript.Character("A", "엄마", "", "woman"),
              new ComicScript.Character("B", "아들", "", "boy")),
          List.of(
              new ComicScript.Panel(
                  "기",
                  "wide",
                  "{A} left, {B} right",
                  List.of(new ComicScript.Line("A", "밥 먹자"), new ComicScript.Line("B", "네"))),
              new ComicScript.Panel(
                  "결", "wide", "{B} center", List.of(new ComicScript.Line("B", "잘 먹었습니다")))),
          true,
          List.of());

  private ObjectNode live() {
    return (ObjectNode)
        json.readTree(
            "{\"panels\":[{\"index\":1,\"aspect\":\"16:9\"},{\"index\":2,\"aspect\":\"1:1\"}]}");
  }

  @Test
  @DisplayName("FR-052: 넓은 그림 컷에만 인물 자리를 왼쪽부터 싣고, 화자는 호칭으로, 모르는 얼굴은 null로 둔다")
  void puts() {
    ObjectNode live = live();
    LivePeople.put(
        live,
        List.of(
            Map.of(
                "B",
                new PanelPlacement.Head(0.7, 0.3, 0.2),
                "A",
                new PanelPlacement.Head(0.3123456, 0.35, 0.22),
                PanelPlacement.UNKNOWN,
                new PanelPlacement.Head(0.5, 0.4, 0.1)),
            Map.of("B", new PanelPlacement.Head(0.5, 0.3, 0.2))),
        SCRIPT.callNames());

    var people = live.path("panels").get(0).path("people");
    assertThat(people.size()).isEqualTo(3);
    assertThat(people.get(0).path("speaker").asString()).isEqualTo("엄마");
    assertThat(people.get(0).path("x").asDouble()).isEqualTo(0.312);
    assertThat(people.get(1).path("speaker").isNull()).isTrue();
    assertThat(people.get(2).path("speaker").asString()).isEqualTo("아들");
    assertThat(live.path("panels").get(1).has("people")).isFalse();
  }

  @Test
  @DisplayName("FR-052: 넓은 그림이 있는 컷만 묻고, 넓은 그림이 없는 컷은 빈 자리다")
  void finds() {
    when(placement.locate(any(), anyList(), anyList(), anyMap()))
        .thenReturn(
            new PanelPlacement.Spots(
                List.of(Map.of(), Map.of()),
                List.of(
                    Map.of("A", new PanelPlacement.Head(0.3, 0.3, 0.2)),
                    Map.of("B", new PanelPlacement.Head(0.5, 0.3, 0.2)))));
    BufferedImage wide = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);

    var got =
        LivePeople.find(
            placement, SCRIPT, List.of(Optional.of(wide), Optional.empty()), Map.of(), true);

    assertThat(got.get(0)).containsKey("A");
    assertThat(got.get(1)).isEmpty();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Map<String, Staging.Side>>> planned = ArgumentCaptor.forClass(List.class);
    verify(placement).locate(any(), anyList(), planned.capture(), anyMap());
    assertThat(planned.getValue().get(0)).isNotEmpty();
    assertThat(planned.getValue().get(1)).isEmpty();
  }

  @Test
  @DisplayName("FR-052: 묻지 않거나 넓은 그림이 없거나 실패하면 모두 빈 자리다")
  void none() {
    BufferedImage wide = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);
    var two = List.of(Optional.of(wide), Optional.<BufferedImage>empty());
    assertThat(LivePeople.find(placement, SCRIPT, two, Map.of(), false))
        .containsExactly(Map.of(), Map.of());
    assertThat(
            LivePeople.find(
                placement, SCRIPT, List.of(Optional.empty(), Optional.empty()), Map.of(), true))
        .containsExactly(Map.of(), Map.of());
    assertThat(LivePeople.find(null, SCRIPT, two, Map.of(), true))
        .containsExactly(Map.of(), Map.of());
    verify(placement, never()).locate(any(), anyList(), anyList(), anyMap());
    when(placement.locate(any(), anyList(), anyList(), anyMap()))
        .thenThrow(new IllegalStateException("boom"));
    assertThat(LivePeople.find(placement, SCRIPT, two, Map.of(), true))
        .containsExactly(Map.of(), Map.of());
  }

  @Test
  @DisplayName("FR-052: 예전 재생 정보의 인물 자리는 넓은 그림 컷으로만 옮긴다")
  void copies() {
    var old =
        json.readTree(
            "{\"panels\":[{\"people\":[{\"speaker\":\"엄마\",\"x\":0.3,\"y\":0.3,\"size\":0.2}]},"
                + "{\"people\":[{\"x\":0.3,\"y\":0.3,\"size\":0.2}]}]}");
    ObjectNode live = live();
    assertThat(LivePeople.copy(old, live)).isTrue();
    assertThat(live.path("panels").get(0).path("people").size()).isEqualTo(1);
    assertThat(live.path("panels").get(1).has("people")).isFalse();
    assertThat(LivePeople.copy(json.readTree("{\"panels\":[{}]}"), live())).isFalse();
    assertThat(LivePeople.hasWide(live)).isTrue();
    assertThat(LivePeople.hasWide(json.readTree("{\"panels\":[{\"aspect\":\"1:1\"}]}"))).isFalse();
    assertThat(LivePeople.round(1.4)).isEqualTo(1.0);
    assertThat(LivePeople.references(null, SCRIPT)).isEmpty();
  }
}
