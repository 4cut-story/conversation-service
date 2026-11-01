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
  @DisplayName("FR-006: 조선시대·무협·동물·그림책 컨셉은 대사 말투 규칙이 있고, 일상·수채화·신문은 대화 그대로 둔다")
  void voices() {
    assertThat(Prompts.voiceSystem(Concept.JOSEON)).contains("하오체").contains("주막").contains("조선시대");
    assertThat(Prompts.voiceSystem(Concept.WUXIA)).contains("객잔").doesNotContain("%VOICE%");
    assertThat(Concept.ANIMALS.voice()).contains("해요체");
    assertThat(Concept.PICTURE_BOOK.voice()).contains("그림책");
    assertThat(Concept.DAILY.voice()).isEmpty();
    assertThat(Concept.WATERCOLOR.voice()).isEmpty();
    assertThat(Concept.NEWSPAPER.voice()).isEmpty();
  }
}
