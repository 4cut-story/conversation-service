package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.voice.VoiceLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 라이브 모드 대사 음성 (FR-050): 목소리 배정, 감정, 대사 읽기 단계, 자막 시간, 배경음 줄이기. */
class SpeechTest {

  private static final String ID = "c1";

  private final JsonMapper json = JsonMapper.builder().build();
  private final Map<String, byte[]> store = new HashMap<>();
  private final ObjectStorage storage = storage(store);
  private final ScenarioStore scenarios = new ScenarioStore(storage, json);
  private final VoiceLibrary voices = mock(VoiceLibrary.class);

  static ObjectStorage storage(Map<String, byte[]> store) {
    ObjectStorage s = mock(ObjectStorage.class);
    org.mockito.Mockito.doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1)))
        .when(s)
        .put(any(), any(), any());
    when(s.get(any())).thenAnswer(i -> Optional.ofNullable(store.get(i.getArgument(0))));
    when(s.exists(any())).thenAnswer(i -> store.containsKey(i.getArgument(0)));
    return s;
  }

  private final ComicScript script =
      new ComicScript(
          "t",
          List.of(
              new ComicScript.Character("A", "민지", "고등학생 손녀", "girl, ponytail"),
              new ComicScript.Character("B", "순자", "손녀를 아끼는", "old woman, gray hair"),
              new ComicScript.Character("C", "철수", "옆집 사람", "")),
          List.of(
              new ComicScript.Panel(
                  "기",
                  "wide",
                  "{A} {B}",
                  List.of(
                      new ComicScript.Line("A", "할머니 이거 봐!"),
                      new ComicScript.Line(null, "그날 밤"),
                      new ComicScript.Line("B", "어머 세상에")),
                  "night",
                  List.of(
                      new ComicScript.Face("A", "big smile"),
                      new ComicScript.Face("B", "wide-eyed"))),
              new ComicScript.Panel(
                  "결", "wide", "{A}", List.of(new ComicScript.Line("A", "왜 이래 진짜")))),
          true,
          List.of());

  private StepContext context(String language) {
    return new StepContext(
        new ConversationInfo(1L, ID, "u", language, 1, null, "GYEONGSANG", false),
        Step.SPEAK,
        1,
        null);
  }

  private SpeakHandler handler(boolean enabled) {
    return new SpeakHandler(scenarios, storage, voices, json, enabled);
  }

  @Test
  @DisplayName("FR-050: 외모(영어)와 호칭·설정(한국어)으로 성별·나이대를 정한다")
  void genderAndAge() {
    assertThat(SpeechCast.gender("young woman, bob hair")).isEqualTo("FEMALE");
    assertThat(SpeechCast.gender("boy with a cap, and his mother")).isEqualTo("MALE");
    assertThat(SpeechCast.gender("orange cat")).isNull();
    assertThat(SpeechCast.ageGroup("old man, cane")).isEqualTo("OLD");
    assertThat(SpeechCast.ageGroup("little girl")).isEqualTo("CHILD");
    assertThat(SpeechCast.ageGroup("middle-aged man")).isEqualTo("MIDDLE");
    assertThat(SpeechCast.ageGroup("woman")).isEqualTo("YOUNG");
    assertThat(SpeechCast.ageGroup("orange cat")).isNull();
    var grandpa = new ComicScript.Character("A", "x", "무뚝뚝한", "", "할아버지", null);
    assertThat(SpeechCast.gender(grandpa)).isEqualTo("MALE");
    assertThat(SpeechCast.ageGroup(grandpa)).isEqualTo("OLD");
    var mom = new ComicScript.Character("A", "x", "엄마", "woman, apron");
    assertThat(SpeechCast.ageGroup(mom)).isEqualTo("MIDDLE");
    var unknown = new ComicScript.Character("A", "x", null, null);
    assertThat(SpeechCast.gender(unknown)).isNull();
    assertThat(SpeechCast.ageGroup(unknown)).isNull();
  }

  @Test
  @DisplayName("FR-050: 감정은 그 컷 화자 표정이 먼저이고, 없으면 대사 낱말과 문장 부호로 정한다")
  void emotion() {
    assertThat(SpeechCast.emotion("furious glare", "좋아")).isEqualTo("angry");
    assertThat(SpeechCast.emotion("teary eyes", null)).isEqualTo("sad");
    assertThat(SpeechCast.emotion("gasp", null)).isEqualTo("surprised");
    assertThat(SpeechCast.emotion("grinning", null)).isEqualTo("happy");
    assertThat(SpeechCast.emotion("looking at phone", "짜증나 정말")).isEqualTo("angry");
    assertThat(SpeechCast.emotion(null, "헐 대박")).isEqualTo("surprised");
    assertThat(SpeechCast.emotion(null, "미안해 ㅠㅠ")).isEqualTo("sad");
    assertThat(SpeechCast.emotion(null, "ㅋㅋ 고마워")).isEqualTo("happy");
    assertThat(SpeechCast.emotion("", "밥 먹었어")).isEqualTo("neutral");
  }

  @Test
  @DisplayName("FR-050: 인물마다 겹치지 않게 목소리를 정하고, 화자가 있는 대사만 감정·키와 함께 한 작업으로 읽힌다")
  void enqueues() {
    scenarios.saveScript(ID, script);
    when(voices.match(argThat(q -> q != null && "FEMALE".equals(q.gender())), any()))
        .thenAnswer(
            i -> {
              VoiceLibrary.Query q = i.getArgument(0);
              Set<String> taken = i.getArgument(1);
              assertThat(q.dialect()).isEqualTo("GYEONGSANG");
              String id = "OLD".equals(q.ageGroup()) ? "f-old" : "f-child";
              assertThat(taken).doesNotContain(id);
              return Optional.of(new VoiceLibrary.Voice(id, "n", "female", "x", null, "t", 0.1));
            });
    when(voices.match(argThat(q -> q != null && q.gender() == null), any()))
        .thenReturn(Optional.empty());

    StepOutcome out = handler(true).start(context("ko"));

    assertThat(out).isInstanceOf(StepOutcome.Enqueue.class);
    var e = (StepOutcome.Enqueue) out;
    assertThat(e.type()).isEqualTo(JobType.SPEECH);
    assertThat(e.modelInfo()).contains("f-old");
    var lines = e.payload().path("lines");
    assertThat(lines.size()).isEqualTo(3);
    assertThat(lines.get(0).path("key").asString()).isEqualTo("p1-1");
    assertThat(lines.get(0).path("voiceId").asString()).isEqualTo("f-child");
    assertThat(lines.get(0).path("emotion").asString()).isEqualTo("happy");
    assertThat(lines.get(0).path("uploadPutKey").asString())
        .isEqualTo(WorkKeys.speechAudio(ID, "p1-1"));
    // 해설(화자 없음)은 읽지 않지만 순번은 자막과 같게 센다
    assertThat(lines.get(1).path("key").asString()).isEqualTo("p1-3");
    assertThat(lines.get(1).path("emotion").asString()).isEqualTo("surprised");
    assertThat(lines.get(2).path("emotion").asString()).isEqualTo("angry");
    assertThat(e.payload().path("conversationId").asString()).isEqualTo(ID);
  }

  @Test
  @DisplayName("FR-050: 기능 스위치가 꺼져 있거나, 영어 만화이거나, 목소리를 못 찾으면 바로 합성으로 넘어간다")
  void skips() {
    scenarios.saveScript(ID, script);
    when(voices.match(any(), any())).thenReturn(Optional.empty());

    for (StepOutcome out :
        List.of(
            handler(false).start(context("ko")),
            handler(true).start(context("en")),
            handler(true).start(context("ko")))) {
      assertThat(out).isEqualTo(new StepOutcome.Done(null, Step.COMPOSE));
    }
    assertThat(new String(store.get(WorkKeys.speech(ID)), StandardCharsets.UTF_8))
        .isEqualTo("{\"lines\":{}}");
  }

  @Test
  @DisplayName("FR-050: 워커가 읽은 대사 길이만 남기고 묻지 않은 줄·실패한 줄은 버린 뒤 합성으로 넘어간다")
  void result() {
    var payload =
        json.readTree("{\"lines\":[{\"key\":\"p1-1\"},{\"key\":\"p1-3\"},{\"key\":\"p2-1\"}]}");
    var ctx = new StepContext(context("ko").conversation(), Step.SPEAK, 1, payload);
    var result =
        json.readTree(
            "{\"lines\":[{\"key\":\"p1-1\",\"durationMs\":1500},{\"key\":\"p1-3\",\"durationMs\":900,\"error\":\"x\"},"
                + "{\"key\":\"p9-9\",\"durationMs\":100},{\"key\":\"p2-1\",\"durationMs\":0}]}");

    StepOutcome out = handler(true).onResult(ctx, result);

    assertThat(out).isEqualTo(new StepOutcome.Done(WorkKeys.speech(ID), Step.COMPOSE));
    assertThat(json.readTree(store.get(WorkKeys.speech(ID))).path("lines").toString())
        .isEqualTo("{\"p1-1\":1500}");
  }

  @Test
  @DisplayName("FR-050: 넓은 그림 컷 자막에 음성을 붙이고, 자막 시간을 음성 길이에 맞추고, 말하는 컷은 배경음을 줄인다")
  void playback() {
    var picked =
        new SoundMatcher.Picked("aaaaaaaaaaaaaaaaaaaaa", "밤", "a", "CC0-1.0", "l", "s", null);
    var live =
        LivePlayback.build(
            json,
            script,
            List.of(Optional.of(picked), Optional.of(picked)),
            List.of(),
            List.of(false, false),
            List.of(true, true),
            List.of(),
            List.of(),
            List.of(),
            Map.of("p1-1", 4000, "p1-3", 5000));

    var first = live.path("panels").get(0);
    var captions = first.path("captions");
    assertThat(captions.size()).isEqualTo(3);
    assertThat(captions.get(0).path("startMs").asInt()).isEqualTo(LivePlayback.CAPTION_LEAD_MS);
    assertThat(captions.get(0).path("endMs").asInt())
        .isEqualTo(LivePlayback.CAPTION_LEAD_MS + 4000 + LivePlayback.VOICE_TAIL_MS);
    assertThat(captions.get(0).path("voice").path("path").asString()).isEqualTo("voice/1-1.mp3");
    assertThat(captions.get(0).path("voice").path("durationMs").asInt()).isEqualTo(4000);
    assertThat(captions.get(1).path("voice").isNull()).isTrue();
    assertThat(captions.get(1).path("startMs").asInt())
        .isEqualTo(captions.get(0).path("endMs").asInt() + LivePlayback.VOICE_GAP_MS);
    assertThat(captions.get(2).path("voice").path("path").asString()).isEqualTo("voice/1-3.mp3");
    int duration = first.path("durationMs").asInt();
    assertThat(duration).isGreaterThan(LivePlayback.MAX_MS);
    assertThat(captions.get(2).path("endMs").asInt()).isLessThanOrEqualTo(duration);
    assertThat(first.path("sound").path("duckVolume").asDouble())
        .isEqualTo(LivePlayback.DUCK_VOLUME);

    var second = live.path("panels").get(1);
    assertThat(second.path("sound").has("duckVolume")).isFalse();
    assertThat(second.path("captions").get(0).has("voice")).isFalse();

    // 아주 긴 음성도 컷 최대 길이를 넘지 않는다
    var capped =
        LivePlayback.build(
            json,
            script,
            List.of(),
            List.of(),
            List.of(false, false),
            List.of(true, true),
            List.of(),
            List.of(),
            List.of(),
            Map.of("p1-1", 30000));
    assertThat(capped.path("panels").get(0).path("durationMs").asInt())
        .isEqualTo(LivePlayback.VOICE_MAX_MS);
    // 1:1 컷(말풍선 그림)은 자막이 없어 음성도 붙이지 않는다
    var square =
        LivePlayback.build(
            json,
            script,
            List.of(),
            List.of(),
            List.of(false, false),
            List.of(false, false),
            List.of(),
            List.of(),
            List.of(),
            Map.of("p1-1", 1000));
    assertThat(square.path("panels").get(0).has("captions")).isFalse();
  }

  @Test
  @DisplayName("FR-050: 합성은 읽은 대사 음성을 만화 폴더로 옮기고, 파일이 없는 대사와 넓은 그림이 아닌 컷은 뺀다")
  void composeCopies() {
    store.put(
        WorkKeys.speech(ID), "{\"lines\":{\"p1-1\":1200,\"p1-3\":800,\"p2-1\":700}}".getBytes());
    store.put(WorkKeys.speechAudio(ID, "p1-1"), new byte[] {1});
    store.put(WorkKeys.speechAudio(ID, "p2-1"), new byte[] {2});
    var handler =
        new ComposeHandler(
            scenarios, storage, null, null, null, null, json, null, mock(SoundMatcher.class));

    var got = handler.voices(ID, script, List.of(true, false));

    assertThat(got).containsExactly(Map.entry("p1-1", 1200));
    assertThat(store.get(WorkKeys.liveVoice(ID, 1, 1))).containsExactly(1);
    assertThat(store).doesNotContainKey(WorkKeys.liveVoice(ID, 2, 1));
    assertThat(handler.voices("none", script, List.of(true, true))).isEmpty();
  }

  @Test
  @DisplayName("FR-050: 라이브 재생 정보를 다시 만들 때 예전 자막의 대사 음성을 그대로 쓴다")
  void backfillKeepsVoices() {
    var old =
        json.readTree(
            "{\"panels\":[{\"captions\":[{\"text\":\"a\",\"voice\":{\"path\":\"voice/1-1.mp3\",\"durationMs\":900}},"
                + "{\"text\":\"b\",\"voice\":null}]},{\"captions\":[]}]}");
    assertThat(LiveBackfill.voices(old)).containsExactly(Map.entry("p1-1", 900));
  }
}
