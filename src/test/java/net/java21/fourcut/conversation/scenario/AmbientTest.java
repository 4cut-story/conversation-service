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
  @DisplayName("FR-041·045: 콘티 화면 효과는 컷 배경으로 거르고 빈 컷은 채우며, 필드가 없는 예전 콘티는 그대로다")
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

    // 근거 없는 효과를 지운 컷에도 배경에 맞는 효과(낮 햇빛 → 창빛)를 넣어 모든 컷이 움직인다
    assertThat(kept).containsExactly(List.of("steam"), List.of("sunbeam"), List.of("twinkle"));
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
  @DisplayName("FR-045: 효과가 없는 컷은 배경 문구에 근거가 있는 효과를, 없으면 은은한 빛을 넣는다")
  void fallback() {
    assertThat(Ambient.fallback("bedroom, morning sunlight through the curtains"))
        .containsExactly("dust");
    assertThat(Ambient.fallback("rooftop at night, a shooting star across the sky"))
        .containsExactly("twinkle");
    assertThat(Ambient.fallback("plain white office")).containsExactly("glow");
    assertThat(Ambient.fallback(null)).containsExactly("glow");
  }

  @Test
  @DisplayName("FR-045: 창빛·화면·도시 불빛·구름·비눗방울은 그 배경에서만 맞고, 창빛·구름은 밤 장면이 아니다")
  void sceneEffects() {
    assertThat(Ambient.SUNBEAM.fits("office, big windows, desks")).isTrue();
    assertThat(Ambient.SUNBEAM.fits("bedroom at night, window")).isFalse();
    assertThat(Ambient.SCREEN.fits("newsroom, rows of monitors")).isTrue();
    assertThat(Ambient.BOKEH.fits("busy city street at night, headlights")).isTrue();
    assertThat(Ambient.CLOUDS.fits("park bench, blue sky with clouds")).isTrue();
    assertThat(Ambient.CLOUDS.fits("living room, painting of clouds")).isFalse();
    assertThat(Ambient.CLOUDS.fits("park at night")).isFalse();
    assertThat(Ambient.BUBBLES.fits("kids blowing soap bubbles in the yard")).isTrue();
  }

  @Test
  @DisplayName("FR-045: 채우는 효과는 앞 컷과 겹치지 않는 것을 먼저 고르고, 맞는 것이 앞 컷 것뿐이면 그것을 쓴다")
  void fallbackAvoidsPrevious() {
    String office = "office, big windows, rows of monitors";
    assertThat(Ambient.fallback(office, List.of())).containsExactly("sunbeam");
    assertThat(Ambient.fallback(office, List.of("sunbeam"))).containsExactly("screen");
    assertThat(Ambient.fallback("office, rows of monitors", List.of("screen")))
        .containsExactly("screen");
    assertThat(Ambient.fallback("plain white wall", List.of("glow"))).containsExactly("glow");
    ComicScript script =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(
                panel("{A} types", office, List.of()),
                panel("{A} sighs", office, List.of()),
                panel("{A} smiles", office, List.of())),
            true,
            List.of());
    assertThat(script.withAmbientLimits().panels().stream().map(ComicScript.Panel::ambient))
        .containsExactly(List.of("sunbeam"), List.of("screen"), List.of("sunbeam"));
  }

  @Test
  @DisplayName("FR-046: 별똥별·불꽃놀이·번개는 그 낱말이 배경에 있을 때만 맞는다")
  void events() {
    assertThat(Ambient.METEOR.fits("night sky, a shooting star falls")).isTrue();
    assertThat(Ambient.METEOR.fits("night sky full of stars")).isFalse();
    assertThat(Ambient.FIREWORKS.fits("summer festival, fireworks over the river")).isTrue();
    assertThat(Ambient.LIGHTNING.fits("dark thunderstorm over the city")).isTrue();
    assertThat(Ambient.filter(List.of("meteor", "twinkle"), "night, shooting star, stars"))
        .containsExactly("meteor", "twinkle");
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
