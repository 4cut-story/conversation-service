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
    // 아이는 아이로 확인된 모습만 받는다 (회차 10: 어린 소협에 어른 무림 고수 모습)
    assertThat(accepts("little swordsman, blue robe", "swordsman, blue robe")).isFalse();
    assertThat(accepts("little swordsman, blue robe", "little boy, blue robe")).isTrue();
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
  @DisplayName("REQ-018: 콘티가 인물에 붙인 소품은 캐릭터 모습에 없어도 그림 외모에 남긴다")
  void drawLookKeepsProps() {
    assertThat(
            CharacterLibrary.drawLook(
                "old man, gray robe, long beard", "old swordsman, gray robe, brass telescope"))
        .isEqualTo("old man, gray robe, long beard, brass telescope");
    assertThat(
            CharacterLibrary.drawLook(
                "bear, white shirt, orange overalls", "bear, orange scarf, red camera"))
        .isEqualTo("bear, white shirt, orange overalls, red camera");
    assertThat(
            CharacterLibrary.drawLook(
                "curly brown hair, navy hoodie", "boy, wooden sword, navy hoodie"))
        .isEqualTo("boy, curly brown hair, navy hoodie, wooden sword");
    assertThat(CharacterLibrary.drawLook("young man, gat", "young man, black gat, blue jeogori"))
        .isEqualTo("young man, gat, blue jeogori");
    assertThat(
            CharacterLibrary.drawLook(
                "young woman, blonde bob, round glasses, yellow hoodie",
                "girl, black pigtails, yellow hoodie, blue jeans"))
        .isEqualTo("young woman, blonde bob, round glasses, yellow hoodie, blue jeans");
  }

  @Test
  @DisplayName("FR-006: 캐릭터 모습에 윗옷이 없으면 콘티 윗옷을 붙이고, 참조 그림이 없으면 콘티 신발도 붙인다")
  void drawLookAddsTopAndShoesWithoutImage() {
    assertThat(
            CharacterLibrary.drawLook(
                "old man, white beard, straw hat",
                "old man, white hair, brown checkered shirt, gray trousers, black rubber boots",
                false))
        .isEqualTo(
            "old man, white beard, straw hat, brown checkered shirt, gray trousers, black rubber"
                + " boots");
    assertThat(
            CharacterLibrary.drawLook(
                "old man, white beard, straw hat",
                "old man, white hair, brown checkered shirt, gray trousers, black rubber boots",
                true))
        .isEqualTo("old man, white beard, straw hat, brown checkered shirt, gray trousers");
  }

  @Test
  @DisplayName("FR-006: medium은 신발을, close-up은 아래옷과 신발을 외모에서 뺀다")
  void framedDropsHiddenParts() {
    String look = "young man, short hair, navy sweater, gray jeans, white sneakers";
    assertThat(LookHead.framed(look, "wide")).isEqualTo(look);
    assertThat(LookHead.framed(look, "medium"))
        .isEqualTo("young man, short hair, navy sweater, gray jeans");
    assertThat(LookHead.framed(look, "close-up")).isEqualTo("young man, short hair, navy sweater");
    assertThat(LookHead.framed(null, "close-up")).isNull();
  }

  @Test
  @DisplayName("FR-006: 콘티의 아래옷은 캐릭터 모습에 아래옷이 없을 때만 붙이고, 신발은 참조 그림이 정하게 둔다")
  void drawLookAddsBottomAndShoes() {
    assertThat(
            CharacterLibrary.drawLook(
                "young man, black short hair, navy sweater",
                "young man, short hair, navy sweater, gray jeans, white sneakers"))
        .isEqualTo("young man, black short hair, navy sweater, gray jeans");
    assertThat(
            CharacterLibrary.drawLook(
                "brown bear, white shirt, long orange overall pants",
                "bear, orange overalls, blue jeans, bare fur legs"))
        .isEqualTo("brown bear, white shirt, long orange overall pants, bare fur legs");
    assertThat(
            CharacterLibrary.drawLook(
                "old swordsman, gray robe, black cloth shoes",
                "old swordsman, gray robe, white trousers, brown boots"))
        .isEqualTo("old swordsman, gray robe, black cloth shoes, white trousers");
    assertThat(CharacterLibrary.drawLook("girl, red dress", "girl, pink hoodie and blue jeans"))
        .isEqualTo("girl, red dress");
    assertThat(
            CharacterLibrary.drawLook(
                "brown bear, white shirt, orange overalls",
                "fox, orange scarf, blue jeans, white sneakers"))
        .isEqualTo("brown bear, white shirt, orange overalls");
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
    // 흔한 옷 낱말(apron, cap)만 같으면 직업 옷을 받지 않는다 (회차 10: 경찰 모자를 쓴 아들, 화가 할머니)
    assertThat(LookHead.fitsOutfit("old woman, green apron", "old woman, gray bun, florist apron"))
        .isFalse();
    assertThat(LookHead.fitsOutfit("bear, red cap", "rabbit, police cap, blue overalls")).isFalse();
    assertThat(LookHead.fitsOutfit("old woman, florist apron", "old woman, florist apron"))
        .isTrue();
    assertThat(LookHead.fitsOutfit("girl, red dress", "little girl, pigtails, pink hoodie"))
        .isTrue();
    assertThat(LookHead.fitsOutfit("boy, yellow hoodie", "bear, fire helmet")).isFalse();
    assertThat(LookHead.fitsOutfit("boy", null)).isTrue();
  }

  @Test
  @DisplayName("REQ-018: 긴 머리 남자·짧은 머리 여자 모습은 콘티가 그 머리를 말할 때만 어울린다")
  void fitsHair() {
    // 남자 안내원에 긴 머리 남자 캐릭터가 붙어 여자로 그려지고 남자 목소리가 나왔다
    assertThat(
            LookHead.fitsHair(
                "young man, navy vest", "young man, long black hair, olive overalls", "M"))
        .isFalse();
    assertThat(
            LookHead.fitsHair(
                "young woman, gray blazer", "young woman, short brown hair, red shirt", "F"))
        .isFalse();
    assertThat(
            LookHead.fitsHair(
                "young man, long hair, band t-shirt",
                "young man, long black hair, black shirt",
                "M"))
        .isTrue();
    assertThat(LookHead.fitsHair("young woman", "young woman, long black hair", "F")).isTrue();
    assertThat(LookHead.fitsHair("young woman", "young woman, short red bob hair", "F")).isTrue();
    assertThat(LookHead.fitsHair("young man", "young man, short black hair", "M")).isTrue();
    assertThat(LookHead.fitsHair("young man", "young man, long scarf, black hair", "M")).isTrue();
    assertThat(LookHead.fitsHair("young man", "long black hair", null)).isTrue();
    assertThat(LookHead.fitsHair("young man", null, "M")).isTrue();
    assertThat(LookHead.fitsHair(null, "young man, long black hair", "M")).isFalse();
  }

  @Test
  @DisplayName("REQ-018: 모습 문구의 아이·어른이 DB 나이대와 어긋나면 쓰지 않는다")
  void agesWithDb() {
    assertThat(LookHead.agesWithDb("little girl, brown pigtails", "MIDDLE")).isFalse();
    assertThat(LookHead.agesWithDb("woman, black bob", "CHILD")).isFalse();
    assertThat(LookHead.agesWithDb("little boy, cap", "CHILD")).isTrue();
    assertThat(LookHead.agesWithDb("old man, cane", "OLD")).isTrue();
    assertThat(LookHead.agesWithDb("blue overalls", "YOUNG")).isTrue();
    assertThat(LookHead.agesWithDb("little girl", null)).isTrue();
  }

  @Test
  @DisplayName("FR-006: 콘티 외모의 성별·나이 마디는 캐릭터 모습에 붙이지 않는다")
  void dropsPersonWords() {
    assertThat(
            CharacterLibrary.drawLook(
                "little boy swordsman, child, blue robe, wooden sword",
                "small child, little girl, red robe, straight-leg white trousers",
                true))
        .isEqualTo(
            "little boy swordsman, child, blue robe, wooden sword, straight-leg white trousers");
  }

  @Test
  @DisplayName("REQ-018: 콘티 costume 칸이 없으면 외모에서 역할 옷 마디(제복·가운·갑옷)를 찾는다")
  void costumeOf() {
    assertThat(LookHead.costumeOf(" white doctor coat ", "young woman"))
        .isEqualTo("white doctor coat");
    assertThat(
            LookHead.costumeOf(
                null, "young man, short black hair, olive military uniform, combat helmet, boots"))
        .isEqualTo("olive military uniform, combat helmet");
    assertThat(LookHead.costumeOf("", "old man, gray monk robe")).isEqualTo("gray monk robe");
    assertThat(LookHead.costumeOf(null, "young woman, police uniform")).isEqualTo("police uniform");
    assertThat(LookHead.costumeOf(null, "young woman, pink hoodie, blue jeans")).isEmpty();
    assertThat(LookHead.costumeOf(null, null)).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 캐릭터가 역할 옷을 입었는지는 색이 아니라 역할 낱말로 본다")
  void wears() {
    assertThat(
            LookHead.wears(
                "navy police uniform, police cap", "young man, police cap, police uniform"))
        .isTrue();
    assertThat(LookHead.wears("olive military uniform", "young man, police cap, police uniform"))
        .isFalse();
    assertThat(
            LookHead.wears("olive military uniform", "young man, short black hair, cream sweater"))
        .isFalse();
    assertThat(LookHead.wears("white doctor coat", "woman, doctor coat, stethoscope")).isTrue();
    assertThat(LookHead.wears("white uniform", "man, white uniform, cap")).isTrue();
    assertThat(LookHead.wears("white uniform", "man, cream sweater")).isFalse();
    assertThat(LookHead.wears("", "man, cream sweater")).isTrue();
    assertThat(LookHead.wears("white uniform", null)).isFalse();
  }

  @Test
  @DisplayName("REQ-018: 역할 옷을 입히면 캐릭터 모습의 옷·모자는 빼고 머리·얼굴은 남긴다")
  void dressed() {
    assertThat(
            LookHead.dressed(
                "young man, short black hair, cream sweater, red cap, round glasses",
                "olive military uniform"))
        .isEqualTo("young man, short black hair, round glasses, olive military uniform");
    assertThat(LookHead.dressed("man in hanbok, topknot, white dopo", "silver knight armor"))
        .isEqualTo("man, topknot, silver knight armor");
    assertThat(LookHead.dressed("young woman, red bob", null)).isEqualTo("young woman, red bob");
    assertThat(LookHead.dressed(null, "white doctor coat")).isEqualTo("white doctor coat");
  }
}
