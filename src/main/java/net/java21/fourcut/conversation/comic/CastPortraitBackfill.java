package net.java21.fourcut.conversation.comic;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 얼굴 그림을 잘라 내기 전에 만든 만화에서, 캐릭터를 못 찾은 등장인물의 얼굴 그림을 채운다. 서버가 뜰 때 한 번 뒤에서 돈다. 컷 그림이 남아 있는(7일) 만화만 채울 수
 * 있다. 얼굴 위치를 다시 물어야 해서 외부 LLM을 만화마다 한 번 부르므로, 한 번 본 만화는 표시를 남겨 다시 묻지 않는다.
 */
@Component
class CastPortraitBackfill {

  private static final Logger log = LoggerFactory.getLogger(CastPortraitBackfill.class);

  private final Production production;
  private final PipelineObserver conversations;
  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComposeHandler compose;
  private final PanelPlacement placement;
  private final ContentClient content;
  private final boolean enabled;

  CastPortraitBackfill(
      Production production,
      PipelineObserver conversations,
      ScenarioStore scenarios,
      ObjectStorage storage,
      ComposeHandler compose,
      PanelPlacement placement,
      ContentClient content,
      @Value("${comic.cast-portrait-backfill:false}") boolean enabled) {
    this.production = production;
    this.conversations = conversations;
    this.scenarios = scenarios;
    this.storage = storage;
    this.compose = compose;
    this.placement = placement;
    this.content = content;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    if (enabled) {
      Thread.ofVirtual().name("cast-portrait-backfill").start(this::run);
    }
  }

  /** 다시 묻지 않도록 남기는 표시. 컷 그림과 함께 지워진다. */
  static String doneMark(String id) {
    return WorkKeys.work(id, "cast-portraits.done");
  }

  /** 합성까지 끝난 대화마다 얼굴 그림을 채운다. 하나가 실패해도 나머지는 한다. */
  int run() {
    List<Long> ids = production.composed();
    int sent = 0;
    for (Long id : ids) {
      ConversationInfo conversation = conversations.find(id);
      if (conversation == null) {
        continue;
      }
      String publicId = conversation.publicId();
      try {
        if (fill(publicId)) {
          sent++;
        }
      } catch (HttpClientErrorException.NotFound e) {
        log.debug("지운 만화라 건너뜀: conversation={}", publicId);
        mark(publicId);
      } catch (RuntimeException e) {
        log.warn("등장인물 얼굴 그림 채우기 실패: conversation={} ({})", publicId, e.toString());
      }
    }
    log.info("등장인물 얼굴 그림 채우기: 대화 {}건 중 {}건 보냄", ids.size(), sent);
    return sent;
  }

  /** 한 만화를 채운다. 보낼 것이 없으면 false. */
  boolean fill(String id) {
    if (storage.exists(doneMark(id)) || !storage.exists(WorkKeys.script(id))) {
      return false;
    }
    ComicScript script = scenarios.script(id);
    boolean missing =
        script.characters() != null
            && script.characters().stream()
                .anyMatch(c -> c.lookId() == null && !storage.exists(portraitKey(id, script, c)));
    if (!missing || !storage.exists(WorkKeys.panel(id, 1))) {
      return false;
    }
    List<BufferedImage> panels = compose.panels(id, script);
    PanelPlacement.Spots spots =
        placement.locate(script, panels, Staging.panels(script), compose.references(script));
    Map<String, String> portraits = compose.portraits(id, script, panels, spots.heads());
    if (portraits.isEmpty()) {
      mark(id);
      return false;
    }
    // 보내기에 실패하면(지운 만화 404 말고) 표시를 남기지 않아 다음에 다시 한다
    content.cast(id, ComposeHandler.cast(script, portraits));
    mark(id);
    return true;
  }

  private static String portraitKey(String id, ComicScript script, ComicScript.Character c) {
    return WorkKeys.castPortrait(id, script.characters().indexOf(c));
  }

  private void mark(String id) {
    storage.put(doneMark(id), "1".getBytes(StandardCharsets.UTF_8), "text/plain");
  }
}
