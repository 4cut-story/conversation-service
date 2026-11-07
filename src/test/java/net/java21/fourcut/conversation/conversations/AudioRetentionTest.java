package net.java21.fourcut.conversation.conversations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-009: 전사로 넘어가지 못한 원본 음성은 24시간이 지나면 지운다. 처리 중인 대화의 음성은 남긴다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "conversation.audio-cleanup-delay=1h",
      "DB_SCHEMA=public"
    })
@Import(TestcontainersConfiguration.class)
class AudioRetentionTest {

  @Autowired private ObjectStorage storage;
  @Autowired private ConversationRepository conversations;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate tx;

  @MockitoBean private ContentClient content;

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from conversations");
  }

  private String saved(ConversationStatus status) {
    Conversation c =
        new Conversation(PublicId.generate(), Conversation.Source.RECORDING, "ko", Instant.now());
    if (status != ConversationStatus.RECORDING) {
      c.queue(1);
      if (status != ConversationStatus.QUEUED) {
        c.fail("시험");
      }
    }
    String id = conversations.saveAndFlush(c).getPublicId();
    storage.put(WorkKeys.audioChunk(id, 0), new byte[] {1}, "audio/webm");
    return id;
  }

  private int purgeAfter(Duration later) {
    Integer n = tx.execute(s -> after(later).purge());
    return n == null ? -1 : n;
  }

  private AudioRetention after(Duration later) {
    Clock clock = Clock.fixed(Instant.now().plus(later), ZoneOffset.UTC);
    return new AudioRetention(storage, conversations, clock, Duration.ofHours(24));
  }

  @Test
  @DisplayName("FR-009: 끝나지 않은 녹음과 실패한 대화의 음성은 24시간 뒤 지우고, 처리 중인 대화는 남긴다")
  void purgesStaleAudio() {
    String abandoned = saved(ConversationStatus.RECORDING);
    String failed = saved(ConversationStatus.FAILED);
    String waiting = saved(ConversationStatus.QUEUED);
    String orphan = PublicId.generate();
    storage.put(WorkKeys.audioChunk(orphan, 0), new byte[] {1}, "audio/webm");

    assertThat(purgeAfter(Duration.ofHours(1))).isZero();
    assertThat(storage.exists(WorkKeys.audioChunk(abandoned, 0))).isTrue();

    assertThat(purgeAfter(Duration.ofHours(25))).isEqualTo(3);
    assertThat(storage.exists(WorkKeys.audioChunk(abandoned, 0))).isFalse();
    assertThat(storage.exists(WorkKeys.audioChunk(failed, 0))).isFalse();
    assertThat(storage.exists(WorkKeys.audioChunk(orphan, 0))).isFalse();
    assertThat(storage.exists(WorkKeys.audioChunk(waiting, 0))).isTrue();
    assertThat(conversations.findByPublicId(abandoned).orElseThrow().getStatus())
        .isEqualTo(ConversationStatus.FAILED);
    assertThat(conversations.findByPublicId(abandoned).orElseThrow().getFailureReason())
        .isEqualTo(AudioRetention.ABANDONED);
  }
}
