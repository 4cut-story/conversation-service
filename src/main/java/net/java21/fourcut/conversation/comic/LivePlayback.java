package net.java21.fourcut.conversation.comic;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Motion;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import net.java21.fourcut.conversation.storage.WorkKeys;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 라이브 모드 재생 정보 JSON (docs specs/003-live-mode data-model 2절, common contracts/live-mode.yaml
 * LivePlayback). 화면은 이 JSON만 읽고 재생한다. 그림 경로는 만화 폴더 기준 상대 경로다.
 */
final class LivePlayback {

  static final int VERSION = 1;

  /** 컷을 보여 주는 최소·최대 시간과 대사 한 글자당 더하는 시간. 말풍선을 읽을 시간이 대사 길이에 따라 다르다. */
  static final int MIN_MS = 3500;

  static final int MAX_MS = 8000;
  static final int PER_CHAR_MS = 60;

  static final double VOLUME = 0.8;
  static final int FADE_MS = 800;

  private LivePlayback() {}

  /**
   * @param sounds 컷별 고른 소리(컷과 같은 순서). 못 고른 컷은 빈 값
   * @param moving 컷별 두 번째 그림을 쓰는지(그려졌고 어긋나지 않았는지)
   */
  static ObjectNode build(
      JsonMapper json,
      ComicScript script,
      List<Optional<SoundMatcher.Picked>> sounds,
      List<Boolean> moving) {
    ObjectNode root = json.createObjectNode();
    root.put("version", VERSION);
    ArrayNode panels = root.putArray("panels");
    for (int i = 0; i < script.panels().size(); i++) {
      ComicScript.Panel p = script.panels().get(i);
      ObjectNode panel = panels.addObject();
      int n = i + 1;
      panel.put("index", n);
      panel.put("role", p.role());
      panel.put("image", WorkKeys.livePanelPath(n, false));
      panel.put("durationMs", durationMs(p));
      Optional<SoundMatcher.Picked> sound = i < sounds.size() ? sounds.get(i) : Optional.empty();
      if (sound.isPresent()) {
        panel.set("sound", sound(json, sound.get()));
      } else {
        panel.putNull("sound");
      }
      Optional<Motion> motion = p.liveMotion();
      if (motion.isPresent() && i < moving.size() && Boolean.TRUE.equals(moving.get(i))) {
        ObjectNode m = panel.putObject("motion");
        m.put("type", motion.get().code());
        m.putArray("frames")
            .add(WorkKeys.livePanelPath(n, false))
            .add(WorkKeys.livePanelPath(n, true));
        m.put("intervalMs", motion.get().intervalMs());
        m.put("easing", "ease-in-out");
      } else {
        panel.putNull("motion");
      }
    }
    return root;
  }

  /** 대사 글자 수에 맞춘 보여 주는 시간. */
  static int durationMs(ComicScript.Panel p) {
    int chars =
        p.lines() == null
            ? 0
            : p.lines().stream().mapToInt(l -> l.text() == null ? 0 : l.text().length()).sum();
    return Math.max(MIN_MS, Math.min(MAX_MS, MIN_MS + chars * PER_CHAR_MS));
  }

  private static ObjectNode sound(JsonMapper json, SoundMatcher.Picked s) {
    ObjectNode node = json.createObjectNode();
    node.put("publicId", s.publicId());
    node.put("title", s.title());
    node.put("volume", VOLUME);
    node.put("fadeMs", FADE_MS);
    node.put("loop", true);
    ObjectNode credit = node.putObject("credit");
    credit.put("author", s.author());
    credit.put("license", s.license());
    credit.put("licenseUrl", s.licenseUrl());
    credit.put("sourceUrl", s.sourceUrl());
    credit.put("originalTitle", s.originalTitle());
    return node;
  }
}
