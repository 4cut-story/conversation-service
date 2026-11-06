package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ExtractCorrectionTest {

  private static final String ID = "c1";

  private final JsonMapper json = JsonMapper.builder().build();
  private final MemoryStorage storage = new MemoryStorage();
  private final TranscriptStore transcripts = new TranscriptStore(storage, json);
  private final DirectLlm llm = mock(DirectLlm.class);
  private final ScenarioProperties properties =
      new ScenarioProperties("qwen3:8b", 600, 16384, 0.4, true);
  private final ExtractHandler handler =
      new ExtractHandler(
          transcripts,
          new ScenarioStore(storage, json),
          new LlmJobs(properties, json),
          properties,
          new TranscriptCorrector(llm, json));
  private final StepContext context =
      new StepContext(new ConversationInfo(1L, ID, "u", "ko", 1), Step.EXTRACT, 1, null);

  @BeforeEach
  void setUp() {
    transcripts.save(
        ID,
        new Transcript(
            "ko",
            List.of("A", "B"),
            List.of(
                new Transcript.Utterance("A", 0, 2, "회의 실어"),
                new Transcript.Utterance("B", 3, 5, "나도"))));
  }

  @Test
  @DisplayName("FR-003: Gemini가 있으면 장면 추출 전에 서버에서 전사를 고치고, 장면 추출은 고친 전사를 쓴다")
  void correctsBeforeExtract() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString()))
        .thenReturn(Optional.of("{\"fixes\":[{\"i\":0,\"text\":\"회의 싫어\"}]}"));

    assertThat(handler.start(context)).isInstanceOf(StepOutcome.RunLocal.class);
    StepOutcome outcome = handler.runLocal(context);

    assertThat(outcome).isInstanceOf(StepOutcome.Enqueue.class);
    String prompt = ((StepOutcome.Enqueue) outcome).payload().path("prompt").asString();
    assertThat(prompt).contains("A: 회의 싫어").doesNotContain("실어");
    assertThat(transcripts.load(ID).utterances().get(0).text()).isEqualTo("회의 싫어");
    // 원본은 따로 남아 다시 시도해도 원본에서 고친다
    assertThat(transcripts.loadOriginal(ID).utterances().get(0).text()).isEqualTo("회의 실어");
    handler.runLocal(context);
    assertThat(transcripts.loadOriginal(ID).utterances().get(0).text()).isEqualTo("회의 실어");
  }

  @Test
  @DisplayName("FR-003: 교정이 실패하면 원문 전사로 장면 추출을 이어 간다")
  void keepsRawOnFailure() {
    when(llm.available()).thenReturn(true);
    when(llm.chat(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("x"));

    StepOutcome outcome = handler.runLocal(context);

    assertThat(((StepOutcome.Enqueue) outcome).payload().path("prompt").asString())
        .contains("A: 회의 실어");
    assertThat(storage.exists(WorkKeys.rawTranscript(ID))).isFalse();
  }

  @Test
  @DisplayName("Gemini를 쓸 수 없으면 교정 없이 바로 장면 추출 작업을 넣는다")
  void skipsWithoutGemini() {
    when(llm.available()).thenReturn(false);
    assertThat(handler.start(context)).isInstanceOf(StepOutcome.Enqueue.class);
  }

  /** 메모리 객체 저장소. */
  static class MemoryStorage implements ObjectStorage {
    private final Map<String, byte[]> objects = new HashMap<>();

    @Override
    public String presignGet(String key, Duration ttl) {
      return key;
    }

    @Override
    public String presignPut(String key, String contentType, Duration ttl) {
      return key;
    }

    @Override
    public void put(String key, byte[] body, String contentType) {
      objects.put(key, body);
    }

    @Override
    public Optional<byte[]> get(String key) {
      return Optional.ofNullable(objects.get(key));
    }

    @Override
    public boolean exists(String key) {
      return objects.containsKey(key);
    }

    @Override
    public void deletePrefix(String prefix) {
      objects.keySet().removeIf(k -> k.startsWith(prefix));
    }
  }
}
