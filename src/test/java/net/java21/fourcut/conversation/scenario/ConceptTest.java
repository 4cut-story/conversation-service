package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConceptTest {

  @Test
  @DisplayName("FR-006: 컨셉마다 콘티의 외모 규칙과 그림체가 다르고 기본은 일상 웹툰이다")
  void concepts() {
    assertThat(Prompts.SCRIPT_SYSTEM).contains("young woman").doesNotContain("%LOOK%");
    assertThat(Prompts.scriptSystem(Concept.ANIMALS)).contains("deer").doesNotContain("%LOOK%");
    assertThat(Concept.ANIMALS.negative()).contains("human");
    assertThat(Concept.of("watercolor")).isEqualTo(Concept.WATERCOLOR);
    assertThat(Concept.of(null)).isEqualTo(Concept.DAILY);
    assertThat(Concept.of("없는 컨셉")).isEqualTo(Concept.DAILY);
    assertThat(Concept.chosen("NEWSPAPER", "ANIMALS")).isEqualTo(Concept.NEWSPAPER);
    assertThat(Concept.chosen(null, "ANIMALS")).isEqualTo(Concept.ANIMALS);
    assertThat(Concept.chosen(null, null)).isEqualTo(Concept.DAILY);
    assertThat(Concept.PICTURE_BOOK.label()).isEqualTo("어린이 그림책");
    assertThat(Concept.NEWSPAPER.style()).contains("muted colors");
    assertThat(Prompts.scriptSystem(Concept.JOSEON)).contains("hanbok").contains("jumak");
    assertThat(Prompts.scriptSystem(Concept.WUXIA)).contains("swordsman");
    assertThat(Concept.of("wuxia")).isEqualTo(Concept.WUXIA);
  }

  @Test
  @DisplayName("FR-006: 콘티 지시는 컨셉마다 작가 페르소나로 시작하고 대사를 그 컨셉 말투로 다듬고 맞춤법·띄어쓰기·어색한 표현을 바로잡게 한다")
  void writerAndVoice() {
    for (Concept c : Concept.values()) {
      String system = Prompts.scriptSystem(c);
      assertThat(system).startsWith("너는").contains(c.writer()).contains(c.voice());
      assertThat(system).contains("quote").contains("\"" + c.label() + "\"");
      assertThat(system).contains("맞춤법").contains("띄어쓰기").contains("받아쓰기 오류").contains("어색");
      assertThat(system).doesNotContain("%WRITER%", "%VOICE%", "%CONCEPT%", "%LOOK%");
    }
    assertThat(Concept.WUXIA.writer()).contains("무협지 작가");
    assertThat(Concept.WUXIA.voice()).contains("소협").contains("객잔");
    assertThat(Concept.JOSEON.voice()).contains("하오체");
    assertThat(Prompts.SCRIPT_SCHEMA).contains("\"quote\"");
  }

  @Test
  @DisplayName("FR-005: 콘티 지시는 컷 수를 4~10컷 중 이야기에 맞게 정하게 하고, 스키마도 4~10개를 받는다")
  void panelCountInPrompt() {
    String system = Prompts.scriptSystem(Concept.DAILY);
    assertThat(system)
        .contains("4~10컷")
        .contains("기본은 4컷")
        .contains("늘리는 경우")
        .contains("4컷으로 두는 경우");
    assertThat(Prompts.SCRIPT_SCHEMA).contains("\"minItems\":4,\"maxItems\":10");
  }
}
