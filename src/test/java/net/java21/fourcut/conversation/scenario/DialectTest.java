package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DialectTest {

  @Test
  @DisplayName("FR-005: 사투리를 고르면 콘티 지시가 테마 말투 위에 그 사투리 어미로 대사를 바꾸게 한다")
  void dialectRule() {
    for (Concept c : Concept.values()) {
      for (Dialect d : Dialect.values()) {
        String system = Prompts.scriptSystem(c, d);
        assertThat(system).doesNotContain("%DIALECT%").contains(c.voice());
        if (d == Dialect.NONE) {
          assertThat(system).isEqualTo(Prompts.scriptSystem(c)).doesNotContain("- 사투리(");
        } else {
          assertThat(system)
              .contains("- 사투리(" + d.label() + ")")
              .contains(d.rule())
              .contains("quote는 바꾸지 않는다");
        }
      }
    }
    assertThat(Prompts.scriptSystem(Concept.WUXIA, Dialect.JEOLLA))
        .contains("소협")
        .contains("당께")
        .contains("어미와 말끝·억양은 이 사투리로");
  }

  @Test
  @DisplayName("FR-005: 사투리는 이름으로 찾고, 없거나 모르는 이름이면 사투리 없음이다")
  void of() {
    assertThat(Dialect.of("GYEONGSANG")).isEqualTo(Dialect.GYEONGSANG);
    assertThat(Dialect.of("SEOUL").label()).isEqualTo("서울");
    assertThat(Dialect.of(null)).isEqualTo(Dialect.NONE);
    assertThat(Dialect.of("JEJU")).isEqualTo(Dialect.NONE);
    assertThat(Dialect.NONE.rule()).isEmpty();
  }
}
