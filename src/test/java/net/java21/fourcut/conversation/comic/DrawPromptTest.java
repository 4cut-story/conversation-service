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
  @DisplayName(
      "FR-006: 두 명이 나오는 컷은 two people과 왼쪽부터 인물 줄을 넣고, 장면은 짧은 이름으로 쓴다. 없는 인물은 a friend다."
          + " 두 명 close-up은 한 명만 그려지므로 medium으로 그린다")
  void twoPeople() {
    var panel =
        new ComicScript.Panel("전", "close-up", "{B} looks at {A}, {C} waits, cafe", List.of());
    assertThat(DrawHandler.prompt("s", script, panel))
        .isEqualTo(
            "s, medium shot, two people, (girl, bob hair) on the left, (boy, glasses) on the right,"
                + " the boy looks at the girl, a friend waits, cafe");
  }

  @Test
  @DisplayName("FR-006: 콘티의 인물별 표정과 배경을 장면 뒤에 붙인다. 두 명이면 짧은 이름으로, 혼자면 표정만")
  void facesAndSetting() {
    var duo =
        new ComicScript.Panel(
            "전",
            "medium",
            "{B} hands {A} a phone, burger shop",
            List.of(),
            "burger shop, ordering kiosk, trays of burgers",
            List.of(
                new ComicScript.Face("A", "wide-eyed, hands up"),
                new ComicScript.Face("B", "laughing"),
                new ComicScript.Face("C", "not in scene")));
    assertThat(DrawHandler.prompt("s", script, duo))
        .isEqualTo(
            "s, medium shot, two people, (girl, bob hair) on the left, (boy, glasses) on the right,"
                + " the boy hands the girl a phone, burger shop, the girl wide-eyed, hands up,"
                + " the boy laughing, burger shop, ordering kiosk, trays of burgers");
    var solo =
        new ComicScript.Panel(
            "결",
            "close-up",
            "{A} eats a burger",
            List.of(),
            " ",
            List.of(new ComicScript.Face("A", "teary smile")));
    assertThat(DrawHandler.prompt("s", script, solo))
        .isEqualTo("s, close up shot, (girl, bob hair) eats a burger, teary smile");
  }

  @Test
  @DisplayName("FR-006: 동물 컨셉은 two people 대신 사람이 섞이지 않는 말을 쓴다")
  void animalCrowd() {
    var panel = new ComicScript.Panel("기", "wide", "{A} and {B} talk, {C} waits, cafe", List.of());
    assertThat(
            DrawHandler.prompt(
                "bugs",
                script.withConcept(net.java21.fourcut.conversation.scenario.Concept.ANIMALS),
                panel))
        .isEqualTo(
            "bugs, wide shot, two animal characters, no humans, (girl, bob hair) on the left,"
                + " (boy, glasses) on the right, the girl and the boy talk, another animal waits, cafe");
  }

  @Test
  @DisplayName("FR-006: 컨셉 그림체를 쓰고, 일상 웹툰과 예전 콘티는 설정 그림체를 쓴다")
  void conceptStyle() {
    var props = new ComicProperties(768, "base style", "base neg", "http://x", 0.2, 3, 0.2);
    assertThat(DrawHandler.style(script, props)).isEqualTo("base style");
    assertThat(DrawHandler.negative(script, props)).isEqualTo("base neg");
    var animals = script.withConcept(net.java21.fourcut.conversation.scenario.Concept.ANIMALS);
    assertThat(DrawHandler.style(animals, props)).contains("animal");
    assertThat(DrawHandler.negative(animals, props)).startsWith("base neg, ").contains("human");
  }

  @Test
  @DisplayName("FR-006: 외모 첫 마디가 같은 두 인물은 장면에서도 자리를 붙여 구분한다")
  void sameHead() {
    ComicScript twins =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "민지", "", "young woman, bob hair"),
                new ComicScript.Character("B", "하린", "", "young woman, ponytail")),
            List.of(),
            true,
            List.of());
    var panel = new ComicScript.Panel("승", "medium", "{B} hugs {A}, {B} smiles", List.of());
    assertThat(DrawHandler.prompt("s", twins, panel))
        .isEqualTo(
            "s, medium shot, two people, (young woman, bob hair) on the left, (young woman, ponytail)"
                + " on the right, the young woman on the right hugs the young woman on the left,"
                + " the young woman on the right smiles");
  }

  @Test
  @DisplayName("FR-006: 세 명이 나오는 컷의 인물 줄은 왼쪽, 가운데, 오른쪽 순서다")
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
    var panel = new ComicScript.Panel("전", "wide", "{C} scolds {B} and {A}", List.of());
    assertThat(DrawHandler.prompt("s", trio, panel))
        .isEqualTo(
            "s, wide shot, three people, (girl) on the left, (old man) in the middle, (boy) on the right,"
                + " the old man scolds the boy and the girl");
  }
}
