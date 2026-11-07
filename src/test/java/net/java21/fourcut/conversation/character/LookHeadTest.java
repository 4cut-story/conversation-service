package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LookHeadTest {

  private static boolean accepts(String wanted, String look) {
    return LookHead.of(wanted).accepts(LookHead.of(look));
  }

  @Test
  @DisplayName("REQ-018: 외모 첫 마디에서 동물·성별·나이를 읽는다")
  void parse() {
    assertThat(LookHead.of("young woman, long black hair"))
        .isEqualTo(new LookHead(null, "F", "YOUNG"));
    assertThat(LookHead.of("middle-aged man, chef hat"))
        .isEqualTo(new LookHead(null, "M", "MIDDLE"));
    assertThat(LookHead.of("little girl, pigtails")).isEqualTo(new LookHead(null, "F", "CHILD"));
    assertThat(LookHead.of("old woman")).isEqualTo(new LookHead(null, "F", "OLD"));
    assertThat(LookHead.of("man in hanbok, black gat")).isEqualTo(new LookHead(null, "M", "ADULT"));
    assertThat(LookHead.of("swordswoman, red robe")).isEqualTo(new LookHead(null, "F", null));
    assertThat(LookHead.of("fox, pink jacket")).isEqualTo(new LookHead("fox", null, null));
    assertThat(LookHead.of("곰, 초록 스웨터")).isEqualTo(new LookHead("bear", null, null));
    assertThat(LookHead.of("할머니, 흰 머리")).isEqualTo(new LookHead(null, "F", "OLD"));
    assertThat(LookHead.of(null)).isEqualTo(new LookHead(null, null, null));
  }

  @Test
  @DisplayName("REQ-018: 성별·나이·동물이 다르면 쓰지 않고, 모르는 값은 맞는다고 본다")
  void accepts() {
    assertThat(accepts("young woman, red hair", "young woman, red bob")).isTrue();
    assertThat(accepts("young woman, red hair", "young man, red bob")).isFalse();
    assertThat(accepts("old man", "young man")).isFalse();
    assertThat(accepts("man in hanbok, topknot", "boy in hanbok, topknot")).isFalse();
    assertThat(accepts("man in hanbok, topknot", "old man, gray beard")).isTrue();
    assertThat(accepts("swordsman, white robe", "swordsman, blue robe")).isTrue();
    assertThat(accepts("fox, red hoodie", "dog, red hoodie")).isFalse();
    assertThat(accepts("fox, red hoodie", "fox, blue hoodie")).isTrue();
    assertThat(accepts("young woman", "fox, blue hoodie")).isFalse();
    assertThat(accepts("person in a hat", "old woman, hat")).isTrue();
  }

  @Test
  @DisplayName("REQ-018: 성별을 아는 쪽은 성별을 모르는 모습을 엄격 비교에서 받지 않는다")
  void acceptsKnown() {
    assertThat(LookHead.of("man, glasses").acceptsKnown(LookHead.of("long hair"))).isFalse();
    assertThat(LookHead.of("man, glasses").acceptsKnown(LookHead.of("old man"))).isTrue();
    assertThat(LookHead.of("person").acceptsKnown(LookHead.of("long hair"))).isTrue();
    assertThat(LookHead.of("fox").acceptsKnown(LookHead.of("fox, scarf"))).isTrue();
  }

  @Test
  @DisplayName("REQ-018: 무협·조선 외모도 나이 낱말로 시작하면 노인 자리에 젊은 캐릭터를, 젊은 자리에 노인을 붙이지 않는다")
  void wuxiaJoseonAge() {
    assertThat(accepts("old swordsman, gray robe", "young swordsman, green robe")).isFalse();
    assertThat(accepts("young swordsman, white robe", "old swordsman, gray beard")).isFalse();
    assertThat(accepts("old woman in hanbok, white bun", "young woman in hanbok")).isFalse();
    assertThat(accepts("little swordsman, blue robe", "young swordsman")).isFalse();
    assertThat(accepts("young swordsman, red robe", "young swordsman, blue robe")).isTrue();
  }

  @Test
  @DisplayName("REQ-018: 첫 마디에 나이가 없어도 흰머리·주름이 있으면 노인으로 보고 젊은 캐릭터를 쓰지 않는다")
  void oldFromDetails() {
    assertThat(LookHead.of("woman, gray hair bun, purple cardigan"))
        .isEqualTo(new LookHead(null, "F", "OLD"));
    assertThat(LookHead.of("man, wrinkled face, cane")).isEqualTo(new LookHead(null, "M", "OLD"));
    assertThat(accepts("woman, gray hair bun, cardigan", "young woman, mail carrier uniform"))
        .isFalse();
    assertThat(LookHead.of("young woman, white hair")).isEqualTo(new LookHead(null, "F", "YOUNG"));
    assertThat(LookHead.of("swordsman, white robe")).isEqualTo(new LookHead(null, "M", null));
  }

  @Test
  @DisplayName("REQ-018: 모습 문구에 성별·나이가 없으면 캐릭터 DB 값으로 채워 할아버지 자리에 소녀를 붙이지 않는다")
  void dbFallback() {
    LookHead blonde = LookHead.of("blonde hair, blue overalls");
    assertThat(LookHead.of("old man, gray hair").accepts(blonde)).isTrue();
    assertThat(LookHead.of("old man, gray hair").accepts(blonde.orDb("FEMALE", "CHILD"))).isFalse();
    assertThat(LookHead.of("girl, pigtails").accepts(blonde.orDb("FEMALE", "CHILD"))).isTrue();
    assertThat(LookHead.of("man, suit").orDb("MALE", "MIDDLE"))
        .isEqualTo(new LookHead(null, "M", "MIDDLE"));
    assertThat(LookHead.of("young woman").orDb("MALE", "OLD"))
        .isEqualTo(new LookHead(null, "F", "YOUNG"));
    assertThat(blonde.orDb(null, null)).isEqualTo(blonde);
  }

  @Test
  @DisplayName("REQ-018: 캐릭터 외모에 성별이 없을 때만 콘티 외모 첫 마디를 앞에 붙여 그린다")
  void drawLook() {
    assertThat(
            CharacterLibrary.drawLook("curly brown hair, navy hoodie", "boy, curly hair, hoodie"))
        .isEqualTo("boy, curly brown hair, navy hoodie");
    assertThat(CharacterLibrary.drawLook("young man, navy hoodie", "boy, hoodie"))
        .isEqualTo("young man, navy hoodie");
    assertThat(CharacterLibrary.drawLook("fox, red scarf", "girl, scarf"))
        .isEqualTo("fox, red scarf");
    assertThat(CharacterLibrary.drawLook("blue hoodie", "gray hair, hoodie"))
        .isEqualTo("blue hoodie");
    assertThat(CharacterLibrary.drawLook("blue hoodie", null)).isEqualTo("blue hoodie");
    assertThat(CharacterLibrary.drawLook(null, "old man")).isEqualTo("old man");
  }

  @Test
  @DisplayName("REQ-018: 성별 없이 시작하는 캐릭터 외모에는 DB 성별·나이대 명사를 붙인다")
  void withNoun() {
    assertThat(LookHead.withNoun("curly brown hair, white hoodie", "MALE", "CHILD"))
        .isEqualTo("boy, curly brown hair, white hoodie");
    assertThat(LookHead.withNoun("gray bun, green apron", "FEMALE", "OLD"))
        .isEqualTo("old woman, gray bun, green apron");
    assertThat(LookHead.withNoun("navy suit", "MALE", null)).isEqualTo("man, navy suit");
    assertThat(LookHead.withNoun("young woman, red bob", "MALE", "OLD"))
        .isEqualTo("young woman, red bob");
    assertThat(LookHead.withNoun("fox, red scarf", "FEMALE", "CHILD")).isEqualTo("fox, red scarf");
    assertThat(LookHead.withNoun("navy suit", null, "OLD")).isEqualTo("navy suit");
  }

  @Test
  @DisplayName("REQ-018: 그림체 모습 첫 마디에 나이가 없으면 캐릭터 DB 나이대를 넣는다")
  void withAge() {
    assertThat(LookHead.withAge("man in hanbok, topknot, purple jeogori", "MALE", "OLD"))
        .isEqualTo("old man in hanbok, topknot, purple jeogori");
    assertThat(LookHead.withAge("woman in hanbok, bun", "FEMALE", "YOUNG"))
        .isEqualTo("young woman in hanbok, bun");
    assertThat(LookHead.withAge("swordsman, yellow robe, long sword", "MALE", "CHILD"))
        .isEqualTo("little swordsman, yellow robe, long sword");
    assertThat(LookHead.withAge("swordswoman, red robe", "FEMALE", "OLD"))
        .isEqualTo("old swordswoman, red robe");
    assertThat(LookHead.withAge("bear, florist apron", "MALE", "OLD"))
        .isEqualTo("old bear, florist apron");
    assertThat(LookHead.withAge("cat, teal hoodie", "FEMALE", "YOUNG"))
        .isEqualTo("cat, teal hoodie");
    // 나이가 이미 있으면 참조 그림도 그 나이라 그대로 둔다
    assertThat(LookHead.withAge("little boy, black crew cut", "MALE", "OLD"))
        .isEqualTo("little boy, black crew cut");
    assertThat(LookHead.withAge("boy in hanbok, braid", "MALE", "CHILD"))
        .isEqualTo("boy in hanbok, braid");
    assertThat(LookHead.withAge("old man, gray crew cut", "MALE", "OLD"))
        .isEqualTo("old man, gray crew cut");
    assertThat(LookHead.withAge("navy suit", "MALE", "MIDDLE"))
        .isEqualTo("middle-aged man, navy suit");
    assertThat(LookHead.withAge("man, navy suit", "MALE", null)).isEqualTo("man, navy suit");
  }

  @Test
  @DisplayName("REQ-018: 콘티가 말하지 않은 직업 옷(경찰복, 의사 가운, 우주복) 모습은 어울리지 않는다")
  void fitsOutfit() {
    assertThat(
            LookHead.fitsOutfit(
                "middle-aged man, gray sweater", "middle-aged man, white doctor coat"))
        .isFalse();
    assertThat(LookHead.fitsOutfit("young man, space suit", "young man, space helmet, space suit"))
        .isTrue();
    assertThat(
            LookHead.fitsOutfit(
                "young woman, police uniform", "young woman, police cap, police uniform"))
        .isTrue();
    assertThat(LookHead.fitsOutfit("old woman, green apron", "old woman, gray bun, florist apron"))
        .isTrue();
    assertThat(LookHead.fitsOutfit("girl, red dress", "little girl, pigtails, pink hoodie"))
        .isTrue();
    assertThat(LookHead.fitsOutfit("boy, yellow hoodie", "bear, fire helmet")).isFalse();
    assertThat(LookHead.fitsOutfit("boy", null)).isTrue();
  }
}
