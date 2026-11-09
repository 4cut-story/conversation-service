package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotionTest {

  @Test
  @DisplayName("FR-012: 허용 목록의 값만 받는다. none·빈 값·목록 밖 값은 움직임 없음이고 대소문자·공백은 맞춘다")
  void parse() {
    assertThat(Motion.parse(" Twinkle ")).contains(Motion.TWINKLE);
    assertThat(Motion.parse("none")).isEmpty();
    assertThat(Motion.parse("")).isEmpty();
    assertThat(Motion.parse(null)).isEmpty();
    assertThat(Motion.parse("wave_hands")).isEmpty();
    assertThat(Motion.codes())
        .containsExactly("twinkle", "rain", "snow", "flicker", "shimmer", "petals");
  }

  @Test
  @DisplayName("FR-013: 그 배경 낱말이 컷 배경에 있을 때만 맞고, 실내 비·눈은 아니다")
  void fits() {
    assertThat(Motion.TWINKLE.fits("night, deep navy sky full of twinkling stars")).isTrue();
    assertThat(Motion.TWINKLE.fits("night, orange street lamp")).isFalse();
    assertThat(Motion.RAIN.fits("rainy street, heavy rain")).isTrue();
    assertThat(Motion.RAIN.fits("dim room, rain outside the window")).isFalse();
    assertThat(Motion.SNOW.fits("snowy park, snowflakes falling")).isTrue();
    assertThat(Motion.FLICKER.fits("birthday cake with candles, dark room")).isTrue();
    assertThat(Motion.SHIMMER.fits("sunny beach, blue sea")).isTrue();
    assertThat(Motion.PETALS.fits("cherry blossom road, pink petals")).isTrue();
    assertThat(Motion.PETALS.fits(null)).isFalse();
  }

  @Test
  @DisplayName("FR-013: 콘티 움직임은 맞지 않는 값을 지우고 한 만화에 2컷까지만 남긴다")
  void limits() {
    ComicScript script =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(
                panel("기", "{A} sits on a bench", "night, sky full of stars", "TWINKLE"),
                panel("승", "{A} waves", "bright daylight", "petals"),
                panel("승", "{A} looks up", "night, stars", "twinkle"),
                panel("전", "{A} gasps", "rainy street, rain", "rain"),
                panel("결", "{A} smiles", "night, stars", "none")),
            true,
            List.of());

    List<String> motions =
        script.withMotionLimits().panels().stream().map(ComicScript.Panel::motion).toList();

    assertThat(motions).containsExactly("twinkle", null, "twinkle", null, null);
  }

  @Test
  @DisplayName("FR-002: 움직임 필드가 없는 예전 콘티는 그대로다")
  void oldScript() {
    ComicScript script =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(new ComicScript.Panel("기", "wide", "{A} sits", List.of())),
            true,
            List.of());
    assertThat(script.withMotionLimits()).isSameAs(script);
  }

  @Test
  @DisplayName("FR-012: 대사를 바꿔도 소리 장면과 움직임은 남는다")
  void keptByCopies() {
    ComicScript.Panel p = panel("기", "{A} sits", "night, stars", "twinkle");
    ComicScript.Panel copy = p.withLines(List.of()).withScene("{A} stands");
    assertThat(copy.sound()).isEqualTo("밤 공원, 풀벌레");
    assertThat(copy.motion()).isEqualTo("twinkle");
  }

  private static ComicScript.Panel panel(String role, String scene, String setting, String motion) {
    return new ComicScript.Panel(
        role, "wide", scene, List.of(), setting, List.of(), "밤 공원, 풀벌레", motion);
  }
}
