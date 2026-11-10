package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobTypeTest {

  @Test
  @DisplayName("FR-011: 워커가 올려 둔 모델 묶음을 쓰는 작업 종류를 모두 먼저 준다 (그림 묶음이면 그림과 대사 읽기)")
  void typesForModel() {
    assertThat(JobType.typesForModel("SDXL")).containsExactly("IMAGE", "SPEECH");
    assertThat(JobType.typesForModel("WHISPER")).containsExactly("TRANSCRIBE");
    assertThat(JobType.typesForModel(null)).isEmpty();
    assertThat(JobType.typesForModel("NONE")).isEmpty();
  }
}
