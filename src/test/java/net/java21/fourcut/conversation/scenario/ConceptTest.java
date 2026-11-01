package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConceptTest {

  @Test
  @DisplayName("FR-006: 컨셉마다 콘티의 외모 규칙과 그림체가 다르고 기본은 일상 웹툰이다")
  void concepts() {
    assertThat(Prompts.SCRIPT_SYSTEM).contains("young woman").doesNotContain("%LOOK%");
    assertThat(Prompts.scriptSystem(Concept.INSECTS)).contains("ladybug").doesNotContain("%LOOK%");
    assertThat(Concept.ANIMALS.negative()).contains("human");
    assertThat(Concept.of("watercolor")).isEqualTo(Concept.WATERCOLOR);
    assertThat(Concept.of(null)).isEqualTo(Concept.DAILY);
    assertThat(Concept.of("없는 컨셉")).isEqualTo(Concept.DAILY);
    assertThat(Concept.chosen("NEWSPAPER", "ANIMALS")).isEqualTo(Concept.NEWSPAPER);
    assertThat(Concept.chosen(null, "ANIMALS")).isEqualTo(Concept.ANIMALS);
    assertThat(Concept.chosen(null, null)).isEqualTo(Concept.DAILY);
    assertThat(Concept.PICTURE_BOOK.label()).isEqualTo("어린이 그림책");
    assertThat(Concept.NEWSPAPER.style()).startsWith("black and white");
  }
}
