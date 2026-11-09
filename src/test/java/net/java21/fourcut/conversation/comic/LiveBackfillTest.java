package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LiveBackfillTest {

  private final Production production = mock(Production.class);
  private final PipelineObserver conversations = mock(PipelineObserver.class);
  private final ScenarioStore scenarios = mock(ScenarioStore.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ContentClient content = mock(ContentClient.class);
  private final SoundMatcher sounds = mock(SoundMatcher.class);
  private final JsonMapper json = JsonMapper.builder().build();

  private LiveBackfill backfill(boolean enabled) {
    return new LiveBackfill(
        production, conversations, scenarios, storage, content, sounds, json, enabled);
  }

  /** 화면 효과가 생기기 전 콘티: 효과 없음, 첫 컷은 밤하늘. */
  private static final ComicScript OLD =
      new ComicScript(
          "t",
          List.of(new ComicScript.Character("A", "민지", "", "girl")),
          List.of(
              new ComicScript.Panel(
                  "기",
                  "wide",
                  "{A} sits",
                  List.of(new ComicScript.Line("A", "별이 정말 많다")),
                  "night park, starry sky",
                  List.of(),
                  "밤 공원, 풀벌레",
                  "twinkle"),
              new ComicScript.Panel("결", "wide", "{A} smiles", List.of())),
          true,
          List.of());

  private static final String OLD_LIVE =
      """
      {"version":1,"panels":[
       {"index":1,"aspect":"16:9","depth":"panels/1-depth.png","motion":{"type":"twinkle","frames":["a","b","c"]},"ambient":[]},
       {"index":2,"aspect":"1:1","motion":null,"ambient":[]}]}
      """;

  @Test
  @DisplayName("FR-047: 예전 만화는 그림 쓰임을 그대로 두고 모든 컷에 화면 효과를 채운 재생 정보를 다시 보낸다")
  void rebuilds() {
    when(production.composed()).thenReturn(List.of(1L, 2L, 3L));
    when(conversations.find(1L)).thenReturn(new ConversationInfo(1L, "a", "u", "ko", 1, "DAILY"));
    when(conversations.find(2L)).thenReturn(new ConversationInfo(2L, "b", "u", "ko", 1, "DAILY"));
    when(conversations.find(3L)).thenReturn(new ConversationInfo(3L, "c", "u", "ko", 1, "DAILY"));
    when(storage.get(WorkKeys.liveJson("a")))
        .thenReturn(Optional.of(OLD_LIVE.getBytes(StandardCharsets.UTF_8)));
    when(storage.exists(WorkKeys.script("a"))).thenReturn(true);
    when(scenarios.script("a")).thenReturn(OLD);
    when(sounds.match(any(), anyLong())).thenReturn(List.of(Optional.empty(), Optional.empty()));
    when(sounds.effects(any())).thenReturn(List.of(Optional.empty(), Optional.empty()));
    // b는 재생 정보가 없는 아주 예전 만화, c는 이미 다시 만든 만화
    when(storage.get(WorkKeys.liveJson("b"))).thenReturn(Optional.empty());
    when(storage.exists(LiveBackfill.doneMark("c"))).thenReturn(true);

    assertThat(backfill(true).run()).isEqualTo(1);

    ArgumentCaptor<JsonNode> sent = ArgumentCaptor.forClass(JsonNode.class);
    verify(content).live(eq("a"), sent.capture());
    JsonNode panels = sent.getValue().path("panels");
    assertThat(panels.get(0).path("aspect").asString()).isEqualTo("16:9");
    assertThat(panels.get(0).path("depth").asString()).isEqualTo("panels/1-depth.png");
    assertThat(panels.get(0).path("motion").path("frames").size()).isEqualTo(3);
    assertThat(panels.get(0).path("ambient").get(0).asString()).isEqualTo("twinkle");
    assertThat(panels.get(1).path("ambient").get(0).asString()).isEqualTo("glow");
    assertThat(panels.get(1).path("motion").isNull()).isTrue();
    verify(storage).put(eq(LiveBackfill.doneMark("a")), any(), anyString());
    verify(storage, never()).put(eq(LiveBackfill.doneMark("b")), any(), anyString());
    verify(scenarios, never()).script("c");
  }

  @Test
  @DisplayName("FR-047: 컷 수가 다른 재생 정보는 다시 만들지 않고, 꺼져 있으면 돌지 않는다")
  void skips() {
    assertThat(backfill(true).rebuild(OLD, json.readTree("{\"panels\":[{\"aspect\":\"1:1\"}]}")))
        .isNull();
    backfill(false).onReady();
    verify(production, never()).composed();
  }
}
