package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TranscriptCorrectorTest {

  private final DirectLlm llm = mock(DirectLlm.class);
  private final TranscriptCorrector corrector =
      new TranscriptCorrector(llm, JsonMapper.builder().build());

  private static Transcript transcript(String... texts) {
    List<Transcript.Utterance> list = new ArrayList<>();
    for (int i = 0; i < texts.length; i++) {
      list.add(new Transcript.Utterance(i % 2 == 0 ? "A" : "B", i * 3.0, i * 3.0 + 2, texts[i]));
    }
    return new Transcript("ko", List.of("A", "B"), list);
  }

  @Test
  @DisplayName("FR-003: 잘못 들은 줄만 고치고 화자·시각은 그대로 둔다")
  void fixesMisheardLines() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), contains("[1] B: 같이 갈 레"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":1,\"text\":\"같이 갈래?\"}]}"));

    Transcript raw = transcript("오늘 뭐 해", "같이 갈 레", "좋아");
    Transcript fixed = corrector.correct(raw);

    assertThat(fixed.utterances())
        .extracting(Transcript.Utterance::text)
        .containsExactly("오늘 뭐 해", "같이 갈래?", "좋아");
    assertThat(fixed.utterances().get(1).speaker()).isEqualTo("B");
    assertThat(fixed.utterances().get(1).start()).isEqualTo(3.0);
    assertThat(fixed.speakers()).containsExactly("A", "B");
  }

  @Test
  @DisplayName("FR-003: 원문과 너무 다른 줄(지어낸 내용)과 범위 밖 번호는 버린다")
  void rejectsInventedLines() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString()))
        .thenReturn(
            Optional.of(
                "{\"fixes\":[{\"i\":0,\"text\":\"우리는 내일 제주도로 가족 여행을 떠나기로 했다\"},"
                    + "{\"i\":7,\"text\":\"없는 줄\"},{\"i\":2,\"text\":\"  \"}]}"));

    Transcript raw = transcript("밥 먹었어", "응", "좋아");

    assertThat(corrector.correct(raw)).isSameAs(raw);
  }

  @Test
  @DisplayName("FR-003: Gemini 호출이 실패하면 원문 전사를 그대로 쓴다")
  void keepsRawWhenCallFails() {
    when(llm.available()).thenReturn(false);
    when(llm.chat(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    Transcript raw = transcript("밥 먹었어", "응");

    assertThat(corrector.correct(raw)).isSameAs(raw);
  }

  @Test
  @DisplayName("FR-003: 응답이 JSON이 아니면 원문을 쓴다")
  void keepsRawOnBadJson() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString())).thenReturn(Optional.of("모르겠어요"));
    Transcript raw = transcript("밥 먹었어");

    assertThat(corrector.correct(raw)).isSameAs(raw);
  }

  @Test
  @DisplayName("긴 전사는 나눠 보내고, 쉬는 중이 되면 아직 보내지 않은 묶음은 보내지 않는다")
  void batchesAndStopsWhenPaused() {
    String[] texts = new String[TranscriptCorrector.BATCH * 3];
    java.util.Arrays.fill(texts, "응");
    when(llm.chat(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    when(llm.available()).thenReturn(true, false);

    corrector.correct(transcript(texts));

    verify(llm, times(1)).chat(anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("FR-003: 묶음들을 동시에 보내고, 묶음마다 고친 줄을 모두 합친다")
  void correctsBatchesConcurrently() {
    String[] texts = new String[TranscriptCorrector.BATCH * 3];
    java.util.Arrays.fill(texts, "응");
    int second = TranscriptCorrector.BATCH;
    int third = TranscriptCorrector.BATCH * 2;
    texts[1] = "같이 갈 레";
    texts[second + 1] = "회의 실어";
    texts[third + 1] = "됬어";
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), contains("(0~"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":1,\"text\":\"같이 갈래?\"}]}"));
    when(llm.chat(anyString(), contains("(" + second + "~"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":" + (second + 1) + ",\"text\":\"회의 싫어\"}]}"));
    when(llm.chat(anyString(), contains("(" + third + "~"), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":" + (third + 1) + ",\"text\":\"됐어\"}]}"));

    Transcript fixed = corrector.correct(transcript(texts));

    verify(llm, times(3)).chat(anyString(), anyString(), anyString());
    assertThat(fixed.utterances().get(1).text()).isEqualTo("같이 갈래?");
    assertThat(fixed.utterances().get(second + 1).text()).isEqualTo("회의 싫어");
    assertThat(fixed.utterances().get(third + 1).text()).isEqualTo("됐어");
  }

  @Test
  @DisplayName("비슷함 판정: 띄어쓰기·몇 글자 고침은 받고, 다른 문장은 버린다")
  void similarity() {
    assertThat(TranscriptCorrector.similar("회의 실어", "회의 싫어")).isTrue();
    assertThat(TranscriptCorrector.similar("같이 갈 레", "같이 갈래?")).isTrue();
    assertThat(TranscriptCorrector.similar("응", "어제 영화 봤어")).isFalse();
    assertThat(TranscriptCorrector.similar("...", "응")).isFalse();
  }

  @Test
  @DisplayName("FR-003: 인식 환각 줄(영상 끝인사 등)만 지우고, 실제 대화는 drop이 와도 지우지 않는다")
  void dropsOnlyHallucinations() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString()))
        .thenReturn(
            Optional.of(
                "{\"fixes\":[{\"i\":1,\"text\":\"시청해 주셔서 감사합니다\",\"drop\":true},"
                    + "{\"i\":2,\"text\":\"좋아\",\"drop\":true}]}"));

    Transcript fixed = corrector.correct(transcript("밥 먹었어", "시청해 주셔서 감사합니다", "좋아"));

    assertThat(fixed.utterances())
        .extracting(Transcript.Utterance::text)
        .containsExactly("밥 먹었어", "좋아");
  }

  @Test
  @DisplayName("FR-003: 기계적으로 되풀이된 말은 한 번으로 줄인다")
  void collapsesRepeats() {
    assertThat(TranscriptCorrector.similar("그래서 그래서 그래서 그래서 그래서", "그래서")).isTrue();
    assertThat(TranscriptCorrector.similar("그래서 우리 내일 영화 보러 가자", "그래서")).isFalse();
  }

  @Test
  @DisplayName("두 번째 묶음부터는 앞 발화 몇 줄을 참고 맥락으로 함께 보낸다")
  void sendsContext() {
    String[] texts = new String[TranscriptCorrector.BATCH + 1];
    java.util.Arrays.fill(texts, "응");
    String prompt =
        TranscriptCorrector.prompt(transcript(texts), TranscriptCorrector.BATCH, texts.length);

    assertThat(prompt)
        .contains("참고 (앞 맥락")
        .contains("[" + (TranscriptCorrector.BATCH - TranscriptCorrector.CONTEXT) + "]")
        .doesNotContain("[" + (TranscriptCorrector.BATCH - TranscriptCorrector.CONTEXT - 1) + "]");
    assertThat(TranscriptCorrector.prompt(transcript("응"), 0, 1)).doesNotContain("참고");
  }
}
