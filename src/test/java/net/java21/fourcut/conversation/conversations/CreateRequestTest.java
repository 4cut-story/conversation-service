package net.java21.fourcut.conversation.conversations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import net.java21.fourcut.conversation.conversations.Conversation.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateRequestTest {

  @Test
  @DisplayName("FR-005: 사투리를 고르지 않거나 없음이면 저장하지 않고, 고르면 그 사투리를 저장한다")
  void chosenDialect() {
    assertThat(new ConversationApi.CreateRequest(Source.UPLOAD, true, "ko").chosenDialect())
        .isNull();
    assertThat(
            new ConversationApi.CreateRequest(Source.UPLOAD, true, "ko", "DAILY", "NONE")
                .chosenDialect())
        .isNull();
    assertThat(
            new ConversationApi.CreateRequest(Source.UPLOAD, true, "ko", null, "JEOLLA")
                .chosenDialect())
        .isEqualTo("JEOLLA");
    assertThat("SEOUL").matches(ConversationApi.DIALECTS);
    assertThat("JEJU").doesNotMatch(ConversationApi.DIALECTS);
  }

  @Test
  @DisplayName("FR-005: 대화는 고른 사투리를 기억하고, 고르지 않으면 사투리 없음이다")
  void conversationDialect() {
    Conversation c = new Conversation("u", Source.UPLOAD, "ko", "DAILY", Instant.EPOCH);
    assertThat(c.getDialect()).isNull();
    c.chooseDialect("GANGWON");
    assertThat(c.getDialect()).isEqualTo("GANGWON");
  }
}
