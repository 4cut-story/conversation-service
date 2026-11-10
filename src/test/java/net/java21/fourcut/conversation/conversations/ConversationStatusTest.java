package net.java21.fourcut.conversation.conversations;

import static org.assertj.core.api.Assertions.assertThat;

import net.java21.fourcut.conversation.pipeline.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConversationStatusTest {

  @Test
  @DisplayName("FR-050: 대사 읽기 단계는 그리기와 합성 사이의 SPEAKING이고, GPU를 기다릴 수 있다")
  void speaking() {
    assertThat(ConversationStatus.of(Step.DRAW)).isEqualTo(ConversationStatus.DRAWING);
    assertThat(ConversationStatus.of(Step.SPEAK)).isEqualTo(ConversationStatus.SPEAKING);
    assertThat(ConversationStatus.of(Step.COMPOSE)).isEqualTo(ConversationStatus.COMPOSING);
    assertThat(ConversationStatus.SPEAKING.progress())
        .isGreaterThan(ConversationStatus.DRAWING.progress())
        .isLessThan(ConversationStatus.COMPOSING.progress());
    assertThat(ConversationStatus.SPEAKING.isProcessing()).isTrue();
    assertThat(ConversationStatus.SPEAKING.isFinal()).isFalse();
  }
}
