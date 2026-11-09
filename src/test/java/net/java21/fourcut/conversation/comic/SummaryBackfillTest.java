package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.StorySummary;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class SummaryBackfillTest {

  private final Production production = mock(Production.class);
  private final PipelineObserver conversations = mock(PipelineObserver.class);
  private final StorySummary summaries = mock(StorySummary.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final ContentClient content = mock(ContentClient.class);

  private SummaryBackfill backfill(boolean enabled) {
    return new SummaryBackfill(production, conversations, summaries, storage, content, enabled);
  }

  private void conversation(long id, String publicId) {
    when(conversations.find(id))
        .thenReturn(new ConversationInfo(id, publicId, "u", "ko", 1, "DAILY"));
  }

  @Test
  @DisplayName("FR-006: 요약을 만든 만화만 보내고 표시를 남긴다. 본 만화·지운 만화·실패는 건너뛴다")
  void fills() {
    when(production.composed()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
    conversation(1L, "a");
    conversation(2L, "b");
    conversation(3L, "c");
    conversation(4L, "d");
    conversation(5L, "e");
    when(conversations.find(6L)).thenReturn(null);
    when(summaries.of("a")).thenReturn(Optional.of("요약 a"));
    when(summaries.of("b")).thenReturn(Optional.empty());
    when(storage.exists(SummaryBackfill.doneMark("c"))).thenReturn(true);
    when(summaries.of("d")).thenReturn(Optional.of("요약 d"));
    doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null))
        .when(content)
        .summary(eq("d"), any());
    when(summaries.of("e")).thenThrow(new IllegalStateException("x"));

    assertThat(backfill(true).run()).isEqualTo(1);
    verify(content).summary("a", new ContentClient.Summary("요약 a"));
    verify(storage).put(eq(SummaryBackfill.doneMark("a")), any(), anyString());
    verify(storage).put(eq(SummaryBackfill.doneMark("d")), any(), anyString());
    verify(storage, never()).put(eq(SummaryBackfill.doneMark("b")), any(), anyString());
    verify(storage, never()).put(eq(SummaryBackfill.doneMark("e")), any(), anyString());
    verify(summaries, never()).of("c");
  }

  @Test
  @DisplayName("FR-006: 꺼져 있으면 뜰 때 돌지 않는다")
  void disabled() {
    backfill(false).onReady();
    verify(production, never()).composed();
  }
}
