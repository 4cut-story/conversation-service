package net.java21.fourcut.conversation.conversations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 한 사람이 하루에 만들 수 있는 만화 수를 넘기면 429 DAILY_LIMIT. 녹음 중인 대화, 관리자, 제외 목록은 세지 않는다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "conversation.audio-cleanup-delay=1h",
      "DB_SCHEMA=public"
    })
@Import(TestcontainersConfiguration.class)
class DailyLimitTest {

  @Autowired private ConversationRepository conversations;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ContentClient content;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from conversations");
  }

  private void started(String owner, int count) {
    for (int i = 0; i < count; i++) {
      Conversation c = new Conversation(owner, Conversation.Source.RECORDING, "ko", Instant.now());
      c.queue(1);
      conversations.saveAndFlush(c);
    }
  }

  private DailyLimit limit(int perDay, String exempt, Instant now) {
    return new DailyLimit(conversations, Clock.fixed(now, ZoneOffset.UTC), perDay, exempt);
  }

  @Test
  @DisplayName("하루 제한: 처리로 넘어간 대화가 제한 수에 닿으면 막고, 녹음 중인 대화는 세지 않는다")
  void blocksAtLimit() {
    String owner = PublicId.generate();
    started(owner, 2);
    conversations.saveAndFlush(
        new Conversation(owner, Conversation.Source.RECORDING, "ko", Instant.now()));
    Instant now = Instant.now();

    assertThatCode(() -> limit(3, "", now).check(owner, "ROLE_MEMBER")).doesNotThrowAnyException();
    assertThatThrownBy(() -> limit(2, "", now).check(owner, "ROLE_MEMBER"))
        .isInstanceOf(ApiException.class)
        .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.DAILY_LIMIT));
    // 다음 날이면 다시 만들 수 있다
    assertThatCode(() -> limit(2, "", now.plusSeconds(86_400 * 2)).check(owner, null))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("하루 제한: 관리자, 제외 목록, 0(제한 없음)은 막지 않는다")
  void exemptions() {
    String owner = PublicId.generate();
    started(owner, 2);
    Instant now = Instant.now();

    assertThatCode(() -> limit(1, "", now).check(owner, "ROLE_MEMBER,ROLE_ADMIN"))
        .doesNotThrowAnyException();
    assertThatCode(() -> limit(1, " other , " + owner, now).check(owner, "ROLE_MEMBER"))
        .doesNotThrowAnyException();
    assertThatCode(() -> limit(0, "", now).check(owner, "ROLE_MEMBER")).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("004 FR-061: RSS 구독으로 만든 대화는 하루 제한에 세지 않는다")
  void rssNotCounted() {
    String owner = PublicId.generate();
    for (int i = 0; i < 3; i++) {
      Conversation c = new Conversation(owner, Conversation.Source.ARTICLE, "ko", Instant.now());
      c.fromFeed(1L, i % 2 == 0);
      c.queue(0);
      conversations.saveAndFlush(c);
    }
    assertThatCode(() -> limit(1, "", Instant.now()).check(owner, "ROLE_MEMBER"))
        .doesNotThrowAnyException();
  }
}
