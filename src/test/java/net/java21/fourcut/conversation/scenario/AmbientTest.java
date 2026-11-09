package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AmbientTest {

  @Test
  @DisplayName("FR-040: 허용 목록의 값만 받고 대소문자·공백은 맞춘다")
  void parse() {
    assertThat(Ambient.parse(" Fireflies ")).contains(Ambient.FIREFLIES);
    assertThat(Ambient.parse("none")).isEmpty();
    assertThat(Ambient.parse(null)).isEmpty();
  }

  @Test
  @DisplayName("FR-041: 그 배경 낱말이 컷 배경에 있을 때만 맞고, 실내 비·눈은 아니다")
  void fits() {
    assertThat(Ambient.TWINKLE.fits("rooftop, night sky full of stars")).isTrue();
    assertThat(Ambient.TWINKLE.fits("night, orange street lamp")).isFalse();
    assertThat(Ambient.RAIN.fits("dim room, rain outside the window")).isFalse();
    assertThat(Ambient.RAIN.fits("rainy alley at night")).isTrue();
    assertThat(Ambient.STEAM.fits("cafe table, a cup of hot coffee")).isTrue();
    assertThat(Ambient.DUST.fits("bedroom, morning sunlight through the curtains")).isTrue();
    assertThat(Ambient.FLICKER.fits("campsite at night, campfire")).isTrue();
    assertThat(Ambient.LEAVES.fits("park path, falling leaves")).isTrue();
    assertThat(Ambient.SNOW.fits(null)).isFalse();
  }

  @Test
  @DisplayName("FR-041: 목록 밖·중복·배경에 없는 값은 빼고 한 컷에 두 개까지만 남긴다")
  void filter() {
    String camp = "campsite at night, campfire, sky full of stars, fireflies";
    assertThat(
            Ambient.filter(List.of("flicker", "FLICKER", "glitter", "twinkle", "fireflies"), camp))
        .containsExactly("flicker", "twinkle");
    assertThat(Ambient.filter(List.of("rain"), "kitchen, rain outside the window")).isEmpty();
    assertThat(Ambient.filter(null, camp)).isEmpty();
  }

  @Test
  @DisplayName("FR-041: 콘티 화면 효과는 컷 배경으로 거르고, 필드가 없는 예전 콘티는 그대로다")
  void limits() {
    ComicScript script =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(
                panel("{A} drinks coffee", "cafe, hot coffee", List.of("steam", "rain")),
                panel("{A} waves", "bright daylight", List.of("twinkle")),
                panel("{A} looks up", "night, stars", List.of("twinkle"))),
            true,
            List.of());

    List<List<String>> kept =
        script.withAmbientLimits().panels().stream().map(ComicScript.Panel::ambient).toList();

    assertThat(kept).containsExactly(List.of("steam"), List.of(), List.of("twinkle"));
    ComicScript old =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(new ComicScript.Panel("기", "wide", "{A} sits", List.of())),
            true,
            List.of());
    assertThat(old.withAmbientLimits()).isSameAs(old);
  }

  @Test
  @DisplayName("FR-040: 대사·장면을 바꿔도 화면 효과는 남는다")
  void keptByCopies() {
    ComicScript.Panel p = panel("{A} sits", "night, stars", List.of("twinkle"));
    assertThat(p.withLines(List.of()).withScene("{A} stands").withMotion("twinkle").ambient())
        .containsExactly("twinkle");
  }

  private static ComicScript.Panel panel(String scene, String setting, List<String> ambient) {
    return new ComicScript.Panel(
        "기", "wide", scene, List.of(), setting, List.of(), null, null, null, ambient);
  }
}
