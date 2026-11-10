package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StirTest {

  private static final String OFFICE = "open office, rows of monitors, wall clock, potted plant";

  @Test
  @DisplayName("FR-049: 배경에 쓰인 물건을 움직이는 구절만 남기고, 인물·없는 물건·너무 짧거나 긴 구절은 지운다")
  void clean() {
    assertThat(Stir.clean("the wall clock's hands tick forward.", OFFICE))
        .contains("the wall clock's hands tick forward");
    assertThat(Stir.clean("the code on the monitors scrolls up", OFFICE)).isPresent();
    // 인물을 움직이면 안 된다
    assertThat(Stir.clean("she taps the keyboard on the desk", OFFICE)).isEmpty();
    assertThat(Stir.clean("{A} glances at the wall clock", OFFICE)).isEmpty();
    // 배경에 없는 물건
    assertThat(Stir.clean("leaves fly around the room slowly", OFFICE)).isEmpty();
    // 너무 짧다·길다·비었다
    assertThat(Stir.clean("clock ticks", OFFICE)).isEmpty();
    assertThat(Stir.clean("the wall clock ticks " + "and ticks ".repeat(12), OFFICE)).isEmpty();
    assertThat(Stir.clean(" ", OFFICE)).isEmpty();
    assertThat(Stir.clean(null, OFFICE)).isEmpty();
    assertThat(Stir.clean("the wall clock's hands tick forward", null)).isEmpty();
  }

  @Test
  @DisplayName("FR-049: 같은 물건을 움직이는지 본다")
  void same() {
    assertThat(Stir.same("the wall clock's hands tick", "the clock hands move again")).isTrue();
    assertThat(Stir.same("the code on the monitors scrolls up", "the wall clock ticks")).isFalse();
    assertThat(Stir.same("the wall clock ticks", null)).isFalse();
  }

  @Test
  @DisplayName("FR-049: 세 장은 움직임의 처음·중간·끝이고, 새 물건을 그리지 말라고 함께 적는다")
  void frames() {
    String s = "steam curls up from the soup pot";
    assertThat(Stir.change(s)).contains("just beginning").contains(s);
    assertThat(Stir.next(s)).contains("halfway").contains(s);
    assertThat(Stir.last(s)).contains("further along").contains(s);
    for (String f : List.of(Stir.change(s), Stir.next(s), Stir.last(s))) {
      assertThat(f).contains("no flying or falling leaves").contains("no new objects");
    }
  }

  @Test
  @DisplayName("FR-049: 바람 컷에만 쓰고, 앞 컷과 같은 물건을 움직이는 구절은 지우며, 대사·장면을 바꿔도 남는다")
  void limits() {
    ComicScript script =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(
                panel(OFFICE, "none", "the wall clock's hands tick forward"),
                panel(OFFICE, "none", "the clock's hands move a little more"),
                panel(OFFICE, "none", "the code on the monitors scrolls up"),
                panel("rooftop, night sky full of stars", "twinkle", "the stars glow brighter")),
            true,
            List.of());

    List<ComicScript.Panel> out = script.withMotionLimits().panels();

    assertThat(out.stream().map(ComicScript.Panel::motion))
        .containsExactly("breeze", "breeze", "breeze", "twinkle");
    assertThat(out.stream().map(ComicScript.Panel::stir))
        .containsExactly(
            "the wall clock's hands tick forward",
            null,
            "the code on the monitors scrolls up",
            null);
    assertThat(out.get(0).liveStir()).contains("the wall clock's hands tick forward");
    assertThat(out.get(3).liveStir()).isEmpty();
    assertThat(out.get(0).withLines(List.of()).withScene("x").withAmbient(List.of()).stir())
        .isEqualTo("the wall clock's hands tick forward");
  }

  private static ComicScript.Panel panel(String setting, String motion, String stir) {
    return new ComicScript.Panel(
        "기", "wide", "{A} works", List.of(), setting, List.of(), null, motion, null, null, stir);
  }
}
