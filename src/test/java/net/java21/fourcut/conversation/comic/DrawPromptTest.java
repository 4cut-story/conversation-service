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
  @DisplayName("FR-006: 그림체 → 인물 외모 → 구도 → 장면 순이고 장면의 {A}는 짧은 이름으로 바뀐다")
  void placeholders() {
    var panel = new ComicScript.Panel("전", "close-up", "{A} drops ice cream, park", List.of());
    assertThat(DrawHandler.prompt("simple cartoon", script, panel))
        .isEqualTo(
            "simple cartoon, girl, bob hair, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, close up shot, girl drops ice cream, park");
  }

  @Test
  @DisplayName("FR-006: 두 명이 나오는 컷은 자리마다 그 인물의 외모·표정만 담은 프롬프트를 함께 보낸다. 한 명이면 보내지 않는다")
  void regions() {
    var duo =
        new ComicScript.Panel(
            "전",
            "medium",
            "{B} hands {A} a phone, cafe",
            List.of(),
            "cafe",
            List.of(new ComicScript.Face("A", "wide-eyed")));
    var stage = Staging.panel(script, duo);
    var regions = DrawHandler.regions("s", script, duo, stage);
    assertThat(regions).hasSize(2);
    assertThat(regions.get(0).path("region").asString())
        .isEqualTo(stage.get("A").name().toLowerCase(java.util.Locale.ROOT));
    assertThat(regions.get(0).path("prompt").asString()).isEqualTo("s, girl, bob hair, wide-eyed");
    assertThat(regions.get(1).path("prompt").asString()).isEqualTo("s, boy, glasses");
    var solo = new ComicScript.Panel("결", "close-up", "{A} eats, cafe", List.of());
    assertThat(DrawHandler.regions("s", script, solo, Staging.panel(script, solo))).isEmpty();
  }

  @Test
  @DisplayName("FR-006: 컷에 나오는 인물마다 자리와 성별·나이를 그린 뒤 확인할 항목으로 보낸다")
  void checks() {
    var duo = new ComicScript.Panel("전", "medium", "{B} hands {A} a phone, cafe", List.of());
    var stage = Staging.panel(script, duo);
    var checks = DrawHandler.checks(script, duo, stage);
    assertThat(checks).hasSize(2);
    assertThat(checks.get(0).path("region").asString())
        .isEqualTo(stage.get("A").name().toLowerCase(java.util.Locale.ROOT));
    assertThat(checks.get(0).path("gender").asString()).isEqualTo("F");
    assertThat(checks.get(0).path("age").asString()).isEqualTo("CHILD");
    assertThat(checks.get(1).path("gender").asString()).isEqualTo("M");
    var animals =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "곰돌", "", "bear, red cap")),
            List.of(),
            true,
            List.of());
    var solo = new ComicScript.Panel("결", "close-up", "{A} eats, cafe", List.of());
    var one = DrawHandler.checks(animals, solo, Staging.panel(animals, solo));
    assertThat(one).hasSize(1);
    assertThat(one.get(0).path("animal").asString()).isEqualTo("bear");
    assertThat(one.get(0).has("gender")).isFalse();
  }

  @Test
  @DisplayName("자리 표시가 없는 예전 콘티는 끝에 인물 외모를 붙인다")
  void legacy() {
    var panel = new ComicScript.Panel("기", null, "two friends at a table", List.of());
    assertThat(DrawHandler.prompt("s", script, panel))
        .isEqualTo(
            "s, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, two friends at a table; girl, bob hair; boy, glasses");
  }

  @Test
  @DisplayName(
      "FR-006: 두 명이 나오는 컷은 나이·성별로 센 인원(a girl and a boy)과 왼쪽부터 인물 줄을 넣고, 장면은 짧은 이름으로 쓴다. 없는 인물은 a friend다."
          + " 두 명 close-up은 한 명만 그려지므로 medium으로 그린다")
  void twoPeople() {
    var panel =
        new ComicScript.Panel("전", "close-up", "{B} looks at {A}, {C} waits, cafe", List.of());
    assertThat(DrawHandler.prompt("s", script, panel))
        .isEqualTo(
            "s, a girl and a boy, side by side, both fully visible, (girl, bob hair) on the left, (boy, glasses) on the right, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, medium shot, the boy looks at the girl, a friend waits, cafe");
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
            "s, a girl and a boy, side by side, both fully visible, (girl, bob hair) on the left, (boy, glasses) on the right, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, medium shot, ordering kiosk, the boy hands the girl a phone, burger shop, the girl wide-eyed, hands up, the boy laughing, burger shop, ordering kiosk, trays of burgers");
    var solo =
        new ComicScript.Panel(
            "결",
            "close-up",
            "{A} eats a burger",
            List.of(),
            " ",
            List.of(new ComicScript.Face("A", "teary smile")));
    assertThat(DrawHandler.prompt("s", script, solo))
        .isEqualTo(
            "s, girl, bob hair, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, close up shot, girl eats a burger, teary smile");
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
            "bugs, two animal characters, no humans, side by side, both fully visible, (girl, bob hair) on the left, (boy, glasses) on the right, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, wide shot, the girl and the boy talk, another animal waits, cafe");
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
            "s, two young women, side by side, both fully visible, (young woman, bob hair) on the left, (young woman, ponytail) on the right, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, medium shot, the young woman on the right hugs the young woman on the left, the young woman on the right smiles");
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
            "s, a girl, an old man and a boy, side by side, both fully visible, (girl) on the left, (old man) in the middle, (boy) on the right, high quality illustration, crisp clean lineart, well-drawn faces, one single scene, wide shot, the old man scolds the boy and the girl");
  }

  @Test
  @DisplayName("FR-006: 동물 컨셉은 종으로 세고, 외모 첫 마디로 셀 수 없으면 two people처럼 센다")
  void whoFallback() {
    java.util.Map<String, Staging.Side> stage =
        java.util.Map.of("A", Staging.Side.LEFT, "B", Staging.Side.RIGHT);
    assertThat(
            DrawHandler.who(
                stage,
                java.util.Map.of("A", "young woman, bob hair", "B", "old man"),
                net.java21.fourcut.conversation.scenario.Concept.DAILY))
        .isEqualTo("a young woman and an old man");
    assertThat(
            DrawHandler.who(
                stage,
                java.util.Map.of("A", "young woman", "B", "robot, silver body"),
                net.java21.fourcut.conversation.scenario.Concept.DAILY))
        .isNull();
    assertThat(
            DrawHandler.who(
                stage,
                java.util.Map.of("A", "young woman", "B", "old man"),
                net.java21.fourcut.conversation.scenario.Concept.ANIMALS))
        .isNull();
    assertThat(
            DrawHandler.who(
                stage,
                java.util.Map.of("A", "squirrel, yellow shirt", "B", "rabbit, blue apron"),
                net.java21.fourcut.conversation.scenario.Concept.ANIMALS))
        .isEqualTo("a squirrel and a rabbit, animal characters, no humans");
    assertThat(
            DrawHandler.who(
                stage,
                java.util.Map.of("A", "fox, red scarf", "B", "fox, green hat"),
                net.java21.fourcut.conversation.scenario.Concept.ANIMALS))
        .isEqualTo("two foxes, animal characters, no humans");
  }

  @Test
  @DisplayName("FR-006: 아이가 나오는 만화의 나이 없는 어른은 tall adult로 그리고, 인원도 그대로 센다")
  void adultsWithChild() {
    var family =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "딸", "", "little girl, pigtails"),
                new ComicScript.Character("B", "엄마", "", "Woman, bun hair, yellow blouse"),
                new ComicScript.Character("C", "할머니", "", "old woman, gray hair"),
                new ComicScript.Character("D", "아빠", "", "man")),
            List.of(),
            true,
            List.of());
    var grown = DrawHandler.adults(family);
    assertThat(grown.characters())
        .extracting(ComicScript.Character::look)
        .containsExactly(
            "little girl, pigtails",
            "tall adult woman, bun hair, yellow blouse",
            "old woman, gray hair",
            "tall adult man");
    var panel = new ComicScript.Panel("기", "wide", "{B} hugs {A}, kitchen", List.of());
    assertThat(DrawHandler.prompt("s", grown, panel))
        .contains("a little girl and a tall adult woman")
        .contains("the tall adult woman hugs the little girl");
    // 아이가 없으면 그대로다
    assertThat(DrawHandler.adults(script)).isSameAs(script);
    var couple =
        new ComicScript(
            "t",
            List.of(new ComicScript.Character("A", "엄마", "", "woman, bob hair")),
            List.of(),
            true,
            List.of());
    assertThat(DrawHandler.adults(couple)).isSameAs(couple);
  }

  @Test
  @DisplayName("FR-006: 무협 아이(little swordsman)는 아이 낱말을 붙여 어른으로 그려지지 않게 한다")
  void wuxiaKids() {
    var heroes =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "소협", "", "little swordsman, blue robe"),
                new ComicScript.Character("B", "단짝", "", "little swordswoman, pink robe"),
                new ComicScript.Character("C", "노고수", "", "old swordsman, gray robe")),
            List.of(),
            true,
            List.of());
    assertThat(DrawHandler.adults(heroes).characters())
        .extracting(ComicScript.Character::look)
        .containsExactly(
            "little boy swordsman, child, blue robe",
            "little girl swordswoman, child, pink robe",
            "old swordsman, gray robe");
  }

  @Test
  @DisplayName("FR-006: 첫 마디가 긴 외모도 나이·성별로 센다(두 남자, 노인과 젊은이)")
  void whoFromLongHeads() {
    var looks =
        java.util.Map.of(
            "A", "young man with glasses, cream sweater", "B", "man in navy hoodie, short hair");
    var stage = java.util.Map.of("A", Staging.Side.LEFT, "B", Staging.Side.RIGHT);
    assertThat(
            DrawHandler.who(stage, looks, net.java21.fourcut.conversation.scenario.Concept.DAILY))
        .isEqualTo("a young man and a man");
    var family =
        java.util.Map.of("A", "curly hair boy with glasses", "B", "grandfather in striped shirt");
    assertThat(
            DrawHandler.who(stage, family, net.java21.fourcut.conversation.scenario.Concept.DAILY))
        .isEqualTo("a boy and an old man");
    var unknown = java.util.Map.of("A", "glasses, blue hoodie", "B", "man in hanbok");
    assertThat(
            DrawHandler.who(stage, unknown, net.java21.fourcut.conversation.scenario.Concept.DAILY))
        .isNull();
    assertThat(
            net.java21.fourcut.conversation.character.CharacterLibrary.personWord(
                "old lady in hanbok"))
        .isEqualTo("old woman");
    assertThat(
            net.java21.fourcut.conversation.character.CharacterLibrary.personWord(
                "tall adult man, beard"))
        .isEqualTo("tall adult man");
    assertThat(
            net.java21.fourcut.conversation.character.CharacterLibrary.personWord(
                "little girl, pigtails"))
        .isEqualTo("little girl");
    assertThat(
            net.java21.fourcut.conversation.character.CharacterLibrary.personWord(
                "middle-aged man, suit"))
        .isEqualTo("middle-aged man");
    assertThat(
            net.java21.fourcut.conversation.character.CharacterLibrary.personWord(
                "rabbit, red scarf"))
        .isNull();
  }

  @Test
  @DisplayName("FR-006: 배경의 밤·새벽·별 같은 빛 조각은 그림체 바로 뒤에 두고, 어두운 때는 낮 하늘을 뺀다")
  void nightLight() {
    var night =
        new ComicScript.Panel(
            "전",
            "wide",
            "{A} looks up, temple",
            List.of(),
            "temple courtyard, night, dark sky full of stars, paper lanterns",
            List.of());
    assertThat(DrawHandler.prompt("ink", script, night))
        .startsWith(
            "ink, night, dark sky full of stars, paper lanterns, dark night lighting, girl, bob hair, high quality");
    assertThat(DrawHandler.dayNegative(night)).contains("daylight");
    var day =
        new ComicScript.Panel(
            "기", "wide", "{A} waves, park", List.of(), "park, benches, trees", List.of());
    assertThat(DrawHandler.light(day)).isEmpty();
    assertThat(DrawHandler.dayNegative(day)).isEmpty();
    var dusk =
        new ComicScript.Panel(
            "기", "wide", "{A} waves", List.of(), "beach, sunset glow, seagulls", List.of());
    assertThat(DrawHandler.light(dusk)).isEqualTo(", sunset glow");
  }

  @Test
  @DisplayName("FR-006: 배경의 첫 물건(핵심 물건)은 구도 바로 뒤에 둔다")
  void keyObjectFirst() {
    var kiosk =
        new ComicScript.Panel(
            "승",
            "close-up",
            "{A} taps the kiosk screen, burger shop",
            List.of(),
            "burger shop, ordering kiosk with menu screen, trays of burgers",
            List.of());
    assertThat(DrawHandler.keyObject(kiosk)).isEqualTo("ordering kiosk with menu screen, ");
    assertThat(DrawHandler.prompt("ink", script, kiosk))
        .contains("close up shot, ordering kiosk with menu screen, ");
    var night =
        new ComicScript.Panel(
            "전", "wide", "{A} looks up", List.of(), "village hill, night, lit cabin", List.of());
    assertThat(DrawHandler.keyObject(night)).isEqualTo("lit cabin, ");
    var bare = new ComicScript.Panel("기", "wide", "{A} waves", List.of(), "park", List.of());
    assertThat(DrawHandler.keyObject(bare)).isEmpty();
  }

  @Test
  @DisplayName("FR-006: 배경 조각 가운데 장면 문장과 겹치는 물건을 핵심 물건으로 고른다")
  void keyObjectMatchesScene() {
    var telescope =
        new ComicScript.Panel(
            "전",
            "medium",
            "{C} steps out beside a huge brass telescope, mountain cabin",
            List.of(),
            "mountain cabin, wooden deck, huge brass telescope on tripod, pine trees",
            List.of());
    assertThat(DrawHandler.keyObject(telescope)).isEqualTo("huge brass telescope on tripod, ");
    var go =
        new ComicScript.Panel(
            "승",
            "medium",
            "{A} places a black stone on the go board, sweating",
            List.of(),
            "hanok room, low wooden table, go board with black and white stones",
            List.of());
    assertThat(DrawHandler.keyObject(go)).isEqualTo("go board with black and white stones, ");
  }
}
