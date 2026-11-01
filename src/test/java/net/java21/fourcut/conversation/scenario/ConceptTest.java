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
    assertThat(Concept.NEWSPAPER.style()).startsWith("black and white");
    assertThat(Prompts.scriptSystem(Concept.JOSEON)).contains("hanbok").contains("jumak");
    assertThat(Prompts.scriptSystem(Concept.WUXIA)).contains("swordsman");
    assertThat(Concept.of("wuxia")).isEqualTo(Concept.WUXIA);
  }

  @Test
  @DisplayName("FR-006: 조선시대·무협·동물·그림책 컨셉은 대사를 그 말투로 바꿔 쓰게 하고, 일상 컨셉은 대화 그대로 둔다")
  void voices() {
    assertThat(Prompts.scriptSystem(Concept.JOSEON)).contains("하오체").contains("주막");
    assertThat(Prompts.scriptSystem(Concept.WUXIA)).contains("무협 소설 말투").contains("객잔");
    assertThat(Prompts.scriptSystem(Concept.ANIMALS)).contains("동화");
    assertThat(Prompts.scriptSystem(Concept.PICTURE_BOOK)).contains("그림책 말투");
    assertThat(Prompts.SCRIPT_SYSTEM).contains("voice는 빈 문자열").doesNotContain("%VOICE%");
    assertThat(Prompts.scriptSchema(Concept.JOSEON)).contains("\"speaker\",\"text\",\"voice\"");
    assertThat(Prompts.scriptSchema(Concept.DAILY)).isEqualTo(Prompts.SCRIPT_SCHEMA);
    assertThat(Concept.NEWSPAPER.voice()).isEmpty();
  }
}
