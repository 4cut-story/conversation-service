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
}
