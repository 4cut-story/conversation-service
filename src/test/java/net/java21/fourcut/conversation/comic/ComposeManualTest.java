package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.character.CharacterLibrary;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ComposeManualTest {

  private static final String ID = "c1";

  private final JsonMapper json = JsonMapper.builder().build();
  private final Memory storage = new Memory();
  private final ScenarioStore scenarios = new ScenarioStore(storage, json);
  private final DirectLlm llm = mock(DirectLlm.class);
  private final ContentClient content = mock(ContentClient.class);
  private final CharacterLibrary characters = mock(CharacterLibrary.class);
  private final ComposeHandler handler =
      new ComposeHandler(
          scenarios,
          storage,
          new ComicComposer(),
          content,
          new PanelPlacement(llm, json),
          characters,
          json,
          mock(Production.class),
          mock(net.java21.fourcut.conversation.sound.SoundMatcher.class));
  private final StepContext context =
      new StepContext(
          new ConversationInfo(1L, ID, "u", "ko", 1, null, null, true), Step.COMPOSE, 1, null);

  @Test
  @DisplayName("FR-006: 테스트 전용 수동 처리 대화는 말풍선 자리 질문을 대기열 작업으로 두고, 답을 받아 합성한다")
  void asksThenComposes() throws Exception {
    when(llm.available()).thenReturn(true);
    when(content.register(any())).thenReturn(new ContentClient.Registered("comic1"));
    scenarios.saveScript(ID, script());
    for (int i = 1; i <= 4; i++) {
      storage.put(WorkKeys.panel(ID, i), png(), "image/png");
    }

    StepOutcome first = handler.runLocal(context);
    assertThat(first).isInstanceOf(StepOutcome.Enqueue.class);
    var payload = ((StepOutcome.Enqueue) first).payload();
    assertThat(payload.path("prompt").asString()).isNotBlank();
    assertThat(payload.path("imageKeys")).hasSize(3);
    assertThat(storage.exists(payload.path("imageKeys").get(0).asString())).isTrue();

    StepOutcome again =
        handler.onResult(context, json.createObjectNode().put("content", "{\"panels\":[]}"));
    assertThat(again).isInstanceOf(StepOutcome.RunLocal.class);

    StepOutcome done = handler.runLocal(context);
    assertThat(done).isInstanceOf(StepOutcome.Completed.class);
    assertThat(((StepOutcome.Completed) done).comicId()).isEqualTo("comic1");
    verify(llm, never()).see(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("REQ-018: 캐릭터를 못 찾은 인물은 컷에서 자른 얼굴 그림을 저장하고 등장인물 소개에 키를 넘긴다")
  void portraits() throws Exception {
    when(content.register(any())).thenReturn(new ContentClient.Registered("comic1"));
    scenarios.saveScript(ID, script());
    for (int i = 1; i <= 4; i++) {
      storage.put(WorkKeys.panel(ID, i), png(), "image/png");
    }
    storage.put(
        ComposeHandler.placementAnswer(ID),
        """
        {"panels":[{"panel":1,"people":[{"speaker":"A","side":"left","x":30,"y":30,"size":20},
        {"speaker":"B","side":"absent"}]}]}"""
            .getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "application/json");

    assertThat(handler.runLocal(context)).isInstanceOf(StepOutcome.Completed.class);

    var sent = org.mockito.ArgumentCaptor.forClass(ContentClient.RegisterComic.class);
    verify(content).register(sent.capture());
    assertThat(sent.getValue().cast())
        .extracting(ContentClient.CastMember::portraitKey)
        .containsExactly(WorkKeys.castPortrait(ID, 0), null);
    assertThat(storage.exists(WorkKeys.castPortrait(ID, 0))).isTrue();
  }

  @Test
  @DisplayName("004 FR-062: 바로 공개(RSS) 대화는 만화 등록에 autoPublish를 보내고, 그 밖의 대화는 보내지 않는다")
  void autoPublish() throws Exception {
    when(content.register(any())).thenReturn(new ContentClient.Registered("comic1"));
    scenarios.saveScript(ID, script());
    for (int i = 1; i <= 4; i++) {
      storage.put(WorkKeys.panel(ID, i), png(), "image/png");
    }
    storage.put(
        ComposeHandler.placementAnswer(ID),
        "{\"panels\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "application/json");
    StepContext rss =
        new StepContext(
            new ConversationInfo(1L, ID, "u", "ko", 1, null, null, true, null, true),
            Step.COMPOSE,
            1,
            null);

    assertThat(handler.runLocal(rss)).isInstanceOf(StepOutcome.Completed.class);
    assertThat(handler.runLocal(context)).isInstanceOf(StepOutcome.Completed.class);

    var sent = org.mockito.ArgumentCaptor.forClass(ContentClient.RegisterComic.class);
    verify(content, org.mockito.Mockito.times(2)).register(sent.capture());
    assertThat(sent.getAllValues())
        .extracting(ContentClient.RegisterComic::autoPublish)
        .containsExactly(true, false);
  }

  private static ComicScript script() {
    List<ComicScript.Character> cast =
        List.of(
            new ComicScript.Character("A", "민나", "", "boy, glasses"),
            new ComicScript.Character("B", "시온", "", "old man, white hair"));
    List<ComicScript.Panel> panels =
        List.of(
            new ComicScript.Panel("기", "wide", "{A} asks {B}", List.of(line("A"))),
            new ComicScript.Panel("승", "wide", "{A} smiles", List.of(line("A"))),
            new ComicScript.Panel("전", "wide", "{B} talks to {A}", List.of(line("B"))),
            new ComicScript.Panel("결", "wide", "{A} and {B} plant", List.of()));
    return new ComicScript("t", cast, panels, true, List.of());
  }

  private static ComicScript.Line line(String speaker) {
    return new ComicScript.Line(speaker, "말");
  }

  private static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  private static final class Memory implements ObjectStorage {
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
