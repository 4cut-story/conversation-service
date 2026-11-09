package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class StorySummaryTest {

  private final DirectLlm llm = mock(DirectLlm.class);
  private final ScenarioStore store = mock(ScenarioStore.class);
  private final TranscriptStore transcripts = mock(TranscriptStore.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final StorySummary summaries =
      new StorySummary(llm, store, transcripts, storage, JsonMapper.builder().build());

  private static ComicScript script(String summary) {
    return new ComicScript(
        "사라진 빵",
        List.of(
            new ComicScript.Character("A", "하늘", "", "girl", "손녀", null),
            new ComicScript.Character("B", "도윤", "", "old woman", "할머니", null)),
        List.of(
            new ComicScript.Panel(
                "기", "wide", "x", List.of(new ComicScript.Line("A", "할머니, 빵 어디 갔어?", null)))),
        true,
        List.of(),
        "DAILY",
        summary);
  }

  @Test
  @DisplayName("FR-006: 콘티에 요약이 있으면 LLM을 부르지 않고, 콘티가 없으면 빈 값")
  void ready() {
    when(storage.exists(WorkKeys.script("a"))).thenReturn(true);
    when(store.script("a")).thenReturn(script("  손녀가 빵을 찾는다.  "));
    assertThat(summaries.of("a")).contains("손녀가 빵을 찾는다.");
    assertThat(summaries.of("gone")).isEmpty();
    verify(llm, never()).chat(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("FR-006: 전사가 지워진 예전 만화는 콘티 대사와 호칭으로 묻고, 부를 수 없으면 빈 값")
  void fromScript() {
    when(storage.exists(WorkKeys.script("a"))).thenReturn(true);
    when(store.script("a")).thenReturn(script(null));
    when(store.candidates("a")).thenReturn(List.of());
    when(llm.available()).thenReturn(false);
    assertThat(summaries.of("a")).isEmpty();

    when(llm.available()).thenReturn(true);
    when(llm.chat(eq(DirectLlm.SUMMARY), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("앞말 {\"summary\":\"손녀가 할머니에게 빵의 행방을 묻는다.\"}"));
    assertThat(summaries.of("a")).contains("손녀가 할머니에게 빵의 행방을 묻는다.");
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(llm, org.mockito.Mockito.atLeastOnce())
        .chat(eq(DirectLlm.SUMMARY), anyString(), prompt.capture(), anyString());
    assertThat(prompt.getValue())
        .contains("A=손녀, B=할머니")
        .contains("만화 제목: 사라진 빵")
        .contains("1컷 손녀: 할머니, 빵 어디 갔어?");
  }

  @Test
  @DisplayName("FR-006: 전사가 남아 있으면 장면과 앞뒤 대화로 묻고, 요약에 남은 가명은 호칭으로 바꾼다")
  void fromTranscript() {
    Transcript transcript =
        new Transcript(
            "ko",
            List.of("A", "B"),
            List.of(
                new Transcript.Utterance("A", 10, 12, "할머니 빵 어디 갔어?"),
                new Transcript.Utterance("B", 13, 15, "내가 먹었지")));
    when(storage.exists(WorkKeys.script("a"))).thenReturn(true);
    when(storage.exists(WorkKeys.transcript("a"))).thenReturn(true);
    when(store.script("a")).thenReturn(script(null));
    when(store.names("a")).thenReturn(List.of());
    when(store.candidates("a"))
        .thenReturn(List.of(new SceneCandidate("FUNNY", 5, 20, "빵", "웃김", 0.9, true)));
    when(transcripts.load("a")).thenReturn(transcript);
    String pseudonym = ScriptHandler.cast(transcript, transcript.seed()).get("A");
    when(llm.available()).thenReturn(true);
    when(llm.chat(eq(DirectLlm.SUMMARY), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("{\"summary\":\"" + pseudonym + "가 빵을 찾는다.\"}"));

    String summary = summaries.of("a").orElseThrow();
    assertThat(summary).doesNotContain(pseudonym).contains("손녀");
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(llm).chat(eq(DirectLlm.SUMMARY), anyString(), prompt.capture(), anyString());
    assertThat(prompt.getValue()).contains("[장면 대화").contains("내가 먹었지");
  }

  @Test
  @DisplayName("FR-006: 답이 JSON이 아니거나 요약이 비면 빈 값")
  void badAnswer() {
    when(storage.exists(WorkKeys.script("a"))).thenReturn(true);
    when(store.script("a")).thenReturn(script(null));
    when(store.candidates("a")).thenReturn(List.of());
    when(llm.available()).thenReturn(true);
    when(llm.chat(eq(DirectLlm.SUMMARY), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("모르겠다"), Optional.of("{\"summary\":\"  \"}"));
    assertThat(summaries.of("a")).isEmpty();
    assertThat(summaries.of("a")).isEmpty();
  }
}
