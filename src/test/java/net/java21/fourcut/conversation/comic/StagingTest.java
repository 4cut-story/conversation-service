package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.comic.Staging.Side;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StagingTest {

  private static ComicScript.Panel panel(String scene) {
    return new ComicScript.Panel("기", "wide", scene, null);
  }

  private static ComicScript script(List<ComicScript.Panel> panels, String... speakers) {
    List<ComicScript.Character> cast =
        java.util.Arrays.stream(speakers)
            .map(s -> new ComicScript.Character(s, "이름" + s, "", "look " + s))
            .toList();
    return new ComicScript("t", cast, panels, true, List.of());
  }

  @Test
  @DisplayName("FR-006: 두 인물은 장면 문장 순서가 바뀌어도 네 컷 내내 같은 쪽에 선다")
  void stableSides() {
    ComicScript s =
        script(
            List.of(
                panel("{A} talks to {B}"),
                panel("{B} hands {A} a cup"),
                panel("{B} laughs"),
                panel("{B} and {A} wave")),
            "A",
            "B");
    List<Map<String, Side>> stages = Staging.panels(s);
    for (int i : new int[] {0, 1, 3}) {
      assertThat(stages.get(i)).containsEntry("A", Side.LEFT).containsEntry("B", Side.RIGHT);
      assertThat(stages.get(i).keySet()).containsExactly("A", "B");
    }
    assertThat(stages.get(2)).containsExactly(Map.entry("B", Side.CENTER));
  }

  @Test
  @DisplayName("FR-006: 가장 많이 나오는 두 인물이 고정 자리를 받고, 나머지는 같은 컷 인물의 반대쪽에 선다")
  void mostFrequentGetHome() {
    ComicScript s =
        script(
            List.of(
                panel("{B} talks to {C}"),
                panel("{C} hands {B} a cup"),
                panel("{A} meets {C}"),
                panel("{B} and {C} wave")),
            "A",
            "B",
            "C");
    Map<String, Side> home = Staging.home(s);
    assertThat(home).containsEntry("B", Side.LEFT).containsEntry("C", Side.RIGHT).hasSize(2);
    assertThat(Staging.panel(s, s.panels().get(2)))
        .containsEntry("A", Side.LEFT)
        .containsEntry("C", Side.RIGHT);
    ComicScript other =
        script(
            List.of(panel("{A} meets {B}"), panel("{A} and {B}"), panel("{C} and {A}")),
            "A",
            "B",
            "C");
    assertThat(Staging.panel(other, other.panels().get(2)))
        .containsEntry("A", Side.LEFT)
        .containsEntry("C", Side.RIGHT);
    ComicScript neither =
        script(
            List.of(panel("{A} and {B}"), panel("{A} and {B}"), panel("{C} meets {D}")),
            "A",
            "B",
            "C",
            "D");
    assertThat(Staging.panel(neither, neither.panels().get(2)))
        .containsEntry("C", Side.LEFT)
        .containsEntry("D", Side.RIGHT);
  }

  @Test
  @DisplayName("FR-006: 세 명이 나오는 컷은 고정 자리 인물이 양쪽, 나머지는 가운데다")
  void threeInPanel() {
    ComicScript s =
        script(
            List.of(panel("{A} and {B}"), panel("{B} and {A}"), panel("{C} shouts at {B} and {A}")),
            "A",
            "B",
            "C");
    assertThat(Staging.panel(s, s.panels().get(2)))
        .containsExactly(
            Map.entry("A", Side.LEFT), Map.entry("B", Side.RIGHT), Map.entry("C", Side.CENTER));
  }

  @Test
  @DisplayName("FR-006: 인물 표시가 없는 예전 콘티는 등장인물 순서로 왼쪽·오른쪽이고, 인물이 없으면 빈 자리다")
  void legacyAndEmpty() {
    ComicScript s = script(List.of(panel("two friends")), "A", "B");
    assertThat(Staging.panel(s, s.panels().get(0)))
        .containsEntry("A", Side.LEFT)
        .containsEntry("B", Side.RIGHT);
    ComicScript none = script(List.of(panel("{A} eats")), "B");
    assertThat(Staging.panel(none, none.panels().get(0))).isEmpty();
  }
}
