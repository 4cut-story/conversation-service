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
  @DisplayName("FR-013·049: 콘티 움직임이 맞지 않으면 배경 낱말로 고르고, 없으면 breeze로 모든 컷이 움직인다. 물결은 배경에 물이 있을 때만")
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
                panel("결", "{A} smiles", "night, stars", "none"),
                panel("결", "{A} runs", "bus stop in the rain", "petals"),
                panel(
                    "결", "{A} waves, holding a water bottle", "rooftop, night, stars", "shimmer")),
            true,
            List.of());

    List<String> motions =
        script.withMotionLimits().panels().stream().map(ComicScript.Panel::motion).toList();

    assertThat(motions)
        .containsExactly("twinkle", "breeze", "twinkle", "rain", "twinkle", "rain", "twinkle");
    assertThat(Motion.BREEZE.fits("")).isFalse();
    assertThat(Motion.fallback("living room, fireplace")).contains(Motion.FLICKER);
    assertThat(Motion.fallback("")).isEmpty();
    assertThat(Motion.BREEZE.last()).isNotBlank();
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
    ComicScript.Panel alarm =
        new ComicScript.Panel(
            "기", "wide", "x", List.of(), "s", List.of(), "아침 방, 빗소리", "none", "휴대폰 알람 소리");
    assertThat(alarm.withLines(List.of()).withScene("y").withMotion(null).effect())
        .isEqualTo("휴대폰 알람 소리");
    assertThat(p.effect()).isNull();
  }

  @Test
  @DisplayName("FR-019: 콘티 지시와 스키마에 사건 효과음(effect)이 있다")
  void effectInPrompt() {
    assertThat(Prompts.SCRIPT_SCHEMA).contains("\"effect\"");
    assertThat(Prompts.SCRIPT_SYSTEM).contains("effect:", "휴대폰 알람 소리");
  }

  @Test
  @DisplayName("FR-008: 소리 장면은 시간대·장소·계속 들리는 소리 순서로 쓰고, 한 번 나는 소리와 대사 속 물건은 쓰지 않는다")
  void soundSceneRule() {
    assertThat(Prompts.SCRIPT_SYSTEM)
        .contains("계속 들리는 소리와 그 결", "한 번 나는 소리(병 부딪힘", "대사에 나온 음식·물건 이름은 쓰지 않는다");
  }

  private static ComicScript.Panel panel(String role, String scene, String setting, String motion) {
    return new ComicScript.Panel(
        role, "wide", scene, List.of(), setting, List.of(), "밤 공원, 풀벌레", motion);
  }
}
