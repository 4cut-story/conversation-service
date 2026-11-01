package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranscriptTextTest {

  @Test
  @DisplayName("같은 화자가 이어 말한 발화는 한 줄로 합치고 군말은 지운다")
  void compact() {
    List<Transcript.Utterance> part =
        List.of(
            new Transcript.Utterance("A", 0, 2, "어 근데"),
            new Transcript.Utterance("A", 3, 5, "음... 내 접시 어디 갔어?"),
            new Transcript.Utterance("B", 6, 7, "음"),
            new Transcript.Utterance("B", 8, 9, "Um I ate it"),
            new Transcript.Utterance("A", 20, 21, "어떡해"));
    assertThat(TranscriptText.compact(part))
        .isEqualTo("[00:00] A: 근데 내 접시 어디 갔어?\n[00:08] B: I ate it\n[00:20] A: 어떡해\n");
    assertThat(TranscriptText.clean(null)).isEmpty();
  }
}
