package net.java21.fourcut.conversation.conversations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 음성 보관 기한 (FR-009). 전사가 끝나면 음성을 바로 지우지만, 녹음을 끝내지 않고 나간 대화나 전사가 실패한 대화는 음성이 남는다. 마지막 조각이 올라온 지
 * 24시간이 지난 음성을 지운다. 아직 처리 중인 대화(GPU PC가 꺼져 전사를 기다리는 등)의 음성은 남긴다. 끝나지 않은 녹음은 실패로 바꾼다.
 */
@Component
class AudioRetention {

  static final String AUDIO_ROOT = "audio-raw/";
  static final String ABANDONED = "녹음이 끝나지 않아 원본 음성을 지웠어요.";

  private static final Logger log = LoggerFactory.getLogger(AudioRetention.class);

  private final ObjectStorage storage;
  private final ConversationRepository conversations;
  private final Clock clock;
  private final Duration keep;

  AudioRetention(
      ObjectStorage storage,
      ConversationRepository conversations,
      Clock clock,
      @Value("${conversation.audio-retention:24h}") Duration keep) {
    this.storage = storage;
    this.conversations = conversations;
    this.clock = clock;
    this.keep = keep;
  }

  /** 한 시간마다 기한이 지난 음성을 지운다. 지운 대화 수를 돌려준다. */
  @Scheduled(
      initialDelayString = "${conversation.audio-cleanup-delay:5m}",
      fixedDelayString = "${conversation.audio-cleanup-interval:1h}")
  @Transactional
  public int purge() {
    Instant before = clock.instant().minus(keep);
    Map<String, Instant> latest = new HashMap<>();
    for (ObjectStorage.Stored o : storage.list(AUDIO_ROOT)) {
      String rest = o.key().substring(AUDIO_ROOT.length());
      int slash = rest.indexOf('/');
      if (slash > 0 && o.lastModified() != null) {
        latest.merge(rest.substring(0, slash), o.lastModified(), (a, b) -> a.isAfter(b) ? a : b);
      }
    }
    int purged = 0;
    for (var e : latest.entrySet()) {
      if (e.getValue().isAfter(before)) {
        continue;
      }
      Conversation c = conversations.findByPublicId(e.getKey()).orElse(null);
      if (c != null && c.getStatus().isProcessing()) {
        continue;
      }
      if (c != null && c.getStatus() == ConversationStatus.RECORDING) {
        c.fail(ABANDONED);
      }
      storage.deletePrefix(WorkKeys.audioPrefix(e.getKey()));
      purged++;
    }
    if (purged > 0) {
      log.info("보관 기한이 지난 원본 음성을 지웠다: {}건", purged);
    }
    return purged;
  }
}
