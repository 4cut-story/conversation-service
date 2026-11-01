package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DrawPromptTest {

  private final ComicScript script =
      new ComicScript(
          "t",
          List.of(
              new ComicScript.Character("A", "민지", "", "girl, bob hair"),
              new ComicScript.Character("B", "도윤", "", "boy, glasses")),
          List.of(),
          true,
          List.of());

  @Test
  @DisplayName("FR-006: 그림체 → 구도 → 장면 순이고 {A}, {B}는 외모로 바뀐다")
  void placeholders() {
    var panel = new ComicScript.Panel("전", "close-up", "{A} drops ice cream, park", List.of());
    assertThat(DrawHandler.prompt("simple cartoon", script, panel))
        .isEqualTo("simple cartoon, close up shot, (girl, bob hair) drops ice cream, park");
  }

  @Test
  @DisplayName("자리 표시가 없는 예전 콘티는 끝에 인물 외모를 붙인다")
  void legacy() {
    var panel = new ComicScript.Panel("기", null, "two friends at a table", List.of());
    assertThat(DrawHandler.prompt("s", script, panel))
        .isEqualTo("s, two friends at a table; girl, bob hair; boy, glasses");
  }

  @Test
  @DisplayName("FR-006: 두 명이 나오는 컷은 two people을 넣고, 없는 인물 자리 표시는 a friend로 바꾼다")
  void twoPeople() {
    var panel =
        new ComicScript.Panel("전", "close-up", "{B} looks at {A}, {C} waits, cafe", List.of());
    assertThat(DrawHandler.prompt("s", script, panel))
        .isEqualTo(
            "s, close up shot, two people, (boy, glasses) looks at (girl, bob hair), a friend waits, cafe");
  }

  @Test
  @DisplayName("FR-006: 컨셉 그림체를 쓰고, 일상 웹툰과 예전 콘티는 설정 그림체를 쓴다")
  void conceptStyle() {
    var props = new ComicProperties(768, "base style", "base neg", "http://x");
    assertThat(DrawHandler.style(script, props)).isEqualTo("base style");
    assertThat(DrawHandler.negative(script, props)).isEqualTo("base neg");
    var insects = script.withConcept(net.java21.fourcut.conversation.scenario.Concept.INSECTS);
    assertThat(DrawHandler.style(insects, props)).contains("insect");
    assertThat(DrawHandler.negative(insects, props)).startsWith("base neg, ").contains("human");
  }
}
