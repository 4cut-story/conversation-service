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
}
