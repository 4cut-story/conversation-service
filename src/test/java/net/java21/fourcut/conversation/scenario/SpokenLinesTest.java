package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpokenLinesTest {

  private final List<String> source =
      SpokenLines.sentences("아 미안 미안. 지하철이 중간에 10분 섰어. 오래 기다렸어?\n빵 하나에 40분?");

  @Test
  @DisplayName("FR-005: LLM이 바꿔 적은 대사는 비슷한 원문 문장으로 되돌린다")
  void snapsToSource() {
    assertThat(SpokenLines.snap("지하철이 중간에 10분 석어.", source)).isEqualTo("지하철이 중간에 10분 섰어.");
    assertThat(SpokenLines.snap("완전히 다른 말이야", source)).isEqualTo("완전히 다른 말이야");
    assertThat(SpokenLines.snap(null, source)).isNull();
  }

  @Test
  @DisplayName("FR-005: 말풍선 길이를 넘는 뒷문장은 버리고 첫 문장은 남긴다")
  void trims() {
    String line = "아 미안 미안. 지하철이 중간에 10분 석어. 오래 기다렸어? 빵 하나에 40분?";
    String out = SpokenLines.snap(line, source);
    assertThat(out).isEqualTo("아 미안 미안. 지하철이 중간에 10분 섰어. 오래 기다렸어?");
    assertThat(out.length()).isLessThanOrEqualTo(SpokenLines.MAX_CHARS);
    assertThat(SpokenLines.similarity("", "")).isEqualTo(1);
  }
}
