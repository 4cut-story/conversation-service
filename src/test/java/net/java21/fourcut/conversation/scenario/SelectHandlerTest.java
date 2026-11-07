package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SelectHandlerTest {

  private static Transcript.Utterance u(double start) {
    return new Transcript.Utterance("A", start, start + 2, "말");
  }

  @Test
  @DisplayName("FR-006: 장면 후보가 없으면 말이 가장 많이 오간 3분을 있었던 일을 기록하는 장면으로 쓴다")
  void diary() {
    Transcript t =
        new Transcript(
            "ko", List.of("A"), List.of(u(0), u(400), u(410), u(420), u(500), u(560), u(900)));
    SceneCandidate c = SelectHandler.diary(t).orElseThrow();
    assertThat(c.start()).isEqualTo(400);
    assertThat(c.end()).isEqualTo(562);
    assertThat(c.valid()).isTrue();
    assertThat(c.summary()).contains("일기처럼");
  }

  @Test
  @DisplayName("FR-006: 발화가 하나도 없으면 기록할 장면도 없다")
  void empty() {
    assertThat(SelectHandler.diary(new Transcript("ko", List.of(), List.of()))).isEmpty();
  }
}
