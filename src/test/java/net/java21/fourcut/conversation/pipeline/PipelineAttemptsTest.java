package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PipelineAttemptsTest {

  @Test
  @DisplayName("FR-006: 콘티는 규칙 위반으로 다시 쓰는 일이 잦아 다섯 번까지, 다른 단계는 세 번까지 시도한다")
  void attempts() {
    assertThat(PipelineService.maxAttempts(Step.SCRIPT)).isEqualTo(5);
    assertThat(PipelineService.maxAttempts(Step.DRAW)).isEqualTo(3);
    assertThat(PipelineService.maxAttempts(Step.REVIEW)).isEqualTo(3);
  }
}
