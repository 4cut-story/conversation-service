package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class CastPortraitBackfillTest {

  private final Production production = mock(Production.class);
  private final PipelineObserver conversations = mock(PipelineObserver.class);
  private final ScenarioStore scenarios = mock(ScenarioStore.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ComposeHandler compose = mock(ComposeHandler.class);
  private final PanelPlacement placement = mock(PanelPlacement.class);
  private final ContentClient content = mock(ContentClient.class);

  private CastPortraitBackfill backfill(boolean enabled) {
    return new CastPortraitBackfill(
        production, conversations, scenarios, storage, compose, placement, content, enabled);
  }

  private static ComicScript script(String lookId) {
    return new ComicScript(
        "t",
        List.of(
            new ComicScript.Character("A", "해담", "", "boy", "소협", lookId),
            new ComicScript.Character("B", "송보원", "", "old man", "노고수", "look1")),
        List.of(),
        true,
        List.of());
  }

  private void conversation(long id, String publicId, ComicScript script) {
    when(conversations.find(id))
        .thenReturn(new ConversationInfo(id, publicId, "u", "ko", 1, "WUXIA"));
    when(storage.exists(WorkKeys.script(publicId))).thenReturn(true);
    when(storage.exists(WorkKeys.panel(publicId, 1))).thenReturn(true);
    when(scenarios.script(publicId)).thenReturn(script);
  }

  @Test
  @DisplayName("REQ-018: 캐릭터를 못 찾은 인물이 있는 만화만 얼굴 그림을 채워 보내고, 본 만화는 표시를 남긴다")
  void fills() {
    when(production.composed()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L));
    conversation(1L, "a", script(null));
    conversation(2L, "b", script("look2"));
    conversation(3L, "c", script(null));
    when(storage.exists(CastPortraitBackfill.doneMark("c"))).thenReturn(true);
    conversation(4L, "d", script(null));
    when(conversations.find(5L)).thenReturn(null);
    when(placement.locate(any(), anyList(), anyList(), anyMap()))
        .thenReturn(new PanelPlacement.Spots(List.of(), List.of()));
    when(compose.portraits(anyString(), any(), anyList(), anyList()))
        .thenReturn(Map.of("A", WorkKeys.castPortrait("a", 0)));
    doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null))
        .when(content)
        .cast(eq("d"), anyList());

    assertThat(backfill(true).run()).isEqualTo(1);

    verify(content)
        .cast(
            "a",
            List.of(
                new ContentClient.CastMember("소협", "해담", null, WorkKeys.castPortrait("a", 0)),
                new ContentClient.CastMember("노고수", "송보원", "look1", null)));
    verify(content, never()).cast(eq("b"), anyList());
    verify(content, never()).cast(eq("c"), anyList());
    verify(storage).put(eq(CastPortraitBackfill.doneMark("a")), any(), any());
    verify(storage).put(eq(CastPortraitBackfill.doneMark("d")), any(), any());
  }

  @Test
  @DisplayName("REQ-018: 얼굴을 못 찾으면 보내지 않고, 컷 그림이 없거나 실패한 만화는 건너뛴다")
  void skips() {
    when(production.composed()).thenReturn(List.of(1L, 2L, 3L));
    conversation(1L, "a", script(null));
    when(placement.locate(any(), anyList(), anyList(), anyMap()))
        .thenReturn(new PanelPlacement.Spots(List.of(), List.of()));
    when(compose.portraits(anyString(), any(), anyList(), anyList())).thenReturn(Map.of());
    conversation(2L, "b", script(null));
    when(storage.exists(WorkKeys.panel("b", 1))).thenReturn(false);
    conversation(3L, "c", script(null));
    when(compose.panels(eq("c"), any())).thenThrow(new IllegalStateException("없다"));

    assertThat(backfill(true).run()).isZero();
    verify(content, never()).cast(anyString(), anyList());
  }

  @Test
  @DisplayName("REQ-018: 꺼 두면 서버가 떠도 채우지 않는다")
  void disabled() {
    backfill(false).onReady();
    verify(production, never()).composed();
  }
}
