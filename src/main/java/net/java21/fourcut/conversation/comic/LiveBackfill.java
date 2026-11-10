package net.java21.fourcut.conversation.comic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 예전 만화의 라이브 재생 정보를 지금 규칙으로 다시 만든다(FR-047). 그림은 그대로 두고 화면 효과(빈 컷 채우기)와 배경음만 다시 고른다. 그래픽 카드를 쓰지 않는다.
 * 서버가 뜰 때 한 번 뒤에서 돈다. 콘티가 남아 있는(7일) 만화만 할 수 있다. 규칙이 바뀌면 {@link #ROUND}를 올려 다시 돌린다.
 */
@Component
class LiveBackfill {

  private static final Logger log = LoggerFactory.getLogger(LiveBackfill.class);

  /** 다시 만드는 차례. 올리면 모든 만화를 한 번 더 다시 만든다. */
  static final int ROUND = 1;

  private final Production production;
  private final PipelineObserver conversations;
  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ContentClient content;
  private final SoundMatcher sounds;
  private final JsonMapper json;
  private final boolean enabled;

  LiveBackfill(
      Production production,
      PipelineObserver conversations,
      ScenarioStore scenarios,
      ObjectStorage storage,
      ContentClient content,
      SoundMatcher sounds,
      JsonMapper json,
      @Value("${comic.live-backfill:false}") boolean enabled) {
    this.production = production;
    this.conversations = conversations;
    this.scenarios = scenarios;
    this.storage = storage;
    this.content = content;
    this.sounds = sounds;
    this.json = json;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    if (enabled) {
      Thread.ofVirtual().name("live-backfill").start(this::run);
    }
  }

  /** 다시 하지 않도록 남기는 표시. 콘티와 함께 지워진다. */
  static String doneMark(String id) {
    return WorkKeys.work(id, "live-" + ROUND + ".done");
  }

  int run() {
    // 404여도 표시하지 않는다: content-service가 아직 이 경로를 모르는 채로 먼저 배포돼도 다음에 다시 한다
    return ComposedBackfill.run(production, conversations, log, "라이브 재생 정보", this::fill, id -> {});
  }

  /** 한 만화를 다시 만든다. 콘티나 재생 정보가 없으면 false. */
  boolean fill(String id) {
    if (storage.exists(doneMark(id))) {
      return false;
    }
    Optional<byte[]> old = storage.get(WorkKeys.liveJson(id));
    if (old.isEmpty() || !storage.exists(WorkKeys.script(id))) {
      return false;
    }
    ComicScript script = scenarios.script(id).withAmbientFilled();
    ObjectNode live = rebuild(script, json.readTree(old.get()));
    if (live == null) {
      return false;
    }
    storage.put(
        WorkKeys.liveJson(id),
        json.writeValueAsString(live).getBytes(StandardCharsets.UTF_8),
        "application/json");
    content.live(id, live);
    mark(id);
    return true;
  }

  /** 예전 재생 정보의 그림 쓰임(넓은 그림, 움직임 장수)은 그대로 두고 새 효과·배경음으로 다시 만든다. 컷 수가 다르면 null. */
  ObjectNode rebuild(ComicScript script, JsonNode old) {
    JsonNode panels = old.path("panels");
    if (!panels.isArray() || panels.size() != script.panels().size()) {
      return null;
    }
    List<Boolean> moving = new ArrayList<>();
    List<Boolean> wide = new ArrayList<>();
    List<Boolean> third = new ArrayList<>();
    List<Boolean> depth = new ArrayList<>();
    List<Boolean> fourth = new ArrayList<>();
    for (JsonNode p : panels) {
      depth.add(p.path("depth").isString());
      JsonNode motion = p.path("motion");
      boolean moves = motion.isObject();
      moving.add(moves);
      wide.add("16:9".equals(p.path("aspect").asString("")));
      third.add(moves && motion.path("frames").size() >= 3);
      fourth.add(moves && motion.path("frames").size() >= 4);
    }
    return LivePlayback.build(
        json,
        script,
        sounds.match(ComposeHandler.queries(script), DrawHandler.seedOf(script)),
        sounds.effects(script.panels().stream().map(ComicScript.Panel::effect).toList()),
        moving,
        wide,
        third,
        depth,
        fourth,
        voices(old));
  }

  /** 예전 재생 정보 자막에 붙어 있던 대사 음성(FR-050)을 그대로 쓴다. 음성 파일은 이미 만화 폴더에 있다. */
  static Map<String, Integer> voices(JsonNode old) {
    Map<String, Integer> out = new java.util.LinkedHashMap<>();
    JsonNode panels = old.path("panels");
    for (int i = 0; i < panels.size(); i++) {
      JsonNode captions = panels.get(i).path("captions");
      for (int k = 0; k < captions.size(); k++) {
        int ms = captions.get(k).path("voice").path("durationMs").asInt(0);
        if (ms > 0) {
          out.put(SpeechCast.key(i + 1, k + 1), ms);
        }
      }
    }
    return out;
  }

  private void mark(String id) {
    storage.put(doneMark(id), "1".getBytes(StandardCharsets.UTF_8), "text/plain");
  }
}
