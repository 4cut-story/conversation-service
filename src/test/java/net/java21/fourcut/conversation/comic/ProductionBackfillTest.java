package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.Production;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ProductionBackfillTest {

  private final Production production = mock(Production.class);
  private final ContentClient content = mock(ContentClient.class);
  private final ObjectNode info = JsonMapper.builder().build().createObjectNode().put("version", 1);

  @Test
  @DisplayName("FR-006: 합성까지 끝난 대화마다 제작 정보를 보내고, 지운 만화나 실패한 대화는 건너뛴다")
  void fills() {
    when(production.composed()).thenReturn(List.of(1L, 2L, 3L, 4L));
    when(production.of(1L, true)).thenReturn(Optional.of(new Production.Built("a", info)));
    when(production.of(2L, true)).thenReturn(Optional.of(new Production.Built("b", info)));
    when(production.of(3L, true)).thenReturn(Optional.empty());
    when(production.of(4L, true)).thenReturn(Optional.of(new Production.Built("d", info)));
    doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null))
        .when(content)
        .production(eq("b"), any());
    doThrow(new IllegalStateException("down")).when(content).production(eq("d"), any());

    int sent = new ProductionBackfill(production, content, true).run();

    assertThat(sent).isEqualTo(1);
    verify(content).production("a", info);
  }

  @Test
  @DisplayName("FR-006: 꺼 두면 서버가 떠도 채우지 않는다")
  void disabled() {
    new ProductionBackfill(production, content, false).onReady();
    verify(production, never()).composed();
  }
}
