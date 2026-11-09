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

  /** 소리 크기 비율(marco): 바탕 배경음(장소 소리·배경음악) 60%, 효과음 100%. 바탕 소리가 대사·효과음을 덮지 않게 한다. */
  static final double VOLUME = 0.6;

  static final int FADE_MS = 800;

  /** 사건 효과음은 배경음 위에 한 번, 조금 크게 바로 튼다. */
  static final double EFFECT_VOLUME = 1.0;

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
    return build(json, script, sounds, List.of(), moving);
  }

  /**
   * @param effects 컷별 사건 효과음(컷과 같은 순서). 없는 컷은 빈 값
   */
  static ObjectNode build(
      JsonMapper json,
      ComicScript script,
      List<Optional<SoundMatcher.Picked>> sounds,
      List<Optional<SoundMatcher.Picked>> effects,
      List<Boolean> moving) {
    return build(json, script, sounds, effects, moving, List.of());
  }

  /** 카메라 움직임 순서. 앞뒤 컷이 같은 움직임이 되지 않게 돌아가며 쓴다(FR-035). */
  static final List<String> CAMERAS = List.of("push_in", "pan_right", "pull_out", "pan_left");

  /** 자막이 컷 시작보다 조금 늦게 나오고, 대사 사이에 쉬는 시간. */
  static final int CAPTION_LEAD_MS = 300;

  static final int CAPTION_GAP_MS = 150;

  /**
   * @param wide 컷별 라이브 전용 넓은 그림(16:9, 말풍선 없음)을 쓰는지. 쓰면 aspect 16:9와 대사 자막을 넣는다
   */
  static ObjectNode build(
      JsonMapper json,
      ComicScript script,
      List<Optional<SoundMatcher.Picked>> sounds,
      List<Optional<SoundMatcher.Picked>> effects,
      List<Boolean> moving,
      List<Boolean> wide) {
    return build(json, script, sounds, effects, moving, wide, List.of());
  }

  /**
   * @param third 움직이는 컷별 세 번째 그림(-c)도 쓰는지(그려졌고 어긋나지 않았는지, FR-044)
   */
  static ObjectNode build(
      JsonMapper json,
      ComicScript script,
      List<Optional<SoundMatcher.Picked>> sounds,
      List<Optional<SoundMatcher.Picked>> effects,
      List<Boolean> moving,
      List<Boolean> wide,
      List<Boolean> third) {
    return build(json, script, sounds, effects, moving, wide, third, List.of());
  }

  /**
   * @param depth 넓은 그림 컷별 깊이 지도가 있는지(FR-048). 있으면 화면이 2.5D 패럴랙스로 움직인다
   */
  static ObjectNode build(
      JsonMapper json,
      ComicScript script,
      List<Optional<SoundMatcher.Picked>> sounds,
      List<Optional<SoundMatcher.Picked>> effects,
      List<Boolean> moving,
      List<Boolean> wide,
      List<Boolean> third,
      List<Boolean> depth) {
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
      int duration = durationMs(p);
      panel.put("durationMs", duration);
      if (i < wide.size() && Boolean.TRUE.equals(wide.get(i))) {
        panel.put("aspect", "16:9");
        captions(panel.putArray("captions"), p, script.callNames(), duration);
        if (i < depth.size() && Boolean.TRUE.equals(depth.get(i))) {
          panel.put("depth", WorkKeys.liveDepthPath(n));
        }
      } else {
        panel.put("aspect", "1:1");
      }
      panel.put("camera", CAMERAS.get(i % CAMERAS.size()));
      Optional<SoundMatcher.Picked> sound = i < sounds.size() ? sounds.get(i) : Optional.empty();
      if (sound.isPresent()) {
        // 장소 소리와 배경음악은 같은 바탕 배경음이라 크기가 같다
        panel.set("sound", sound(json, sound.get(), VOLUME, FADE_MS, true));
      } else {
        panel.putNull("sound");
      }
      Optional<SoundMatcher.Picked> effect = i < effects.size() ? effects.get(i) : Optional.empty();
      if (effect.isPresent()) {
        panel.set("effect", sound(json, effect.get(), EFFECT_VOLUME, 0, false));
      } else {
        panel.putNull("effect");
      }
      ArrayNode ambient = panel.putArray("ambient");
      if (p.ambient() != null) {
        p.ambient().forEach(ambient::add);
      }
      Optional<Motion> motion = p.liveMotion();
      if (motion.isPresent() && i < moving.size() && Boolean.TRUE.equals(moving.get(i))) {
        ObjectNode m = panel.putObject("motion");
        m.put("type", motion.get().code());
        ArrayNode frames =
            m.putArray("frames")
                .add(WorkKeys.livePanelPath(n, 0))
                .add(WorkKeys.livePanelPath(n, 1));
        if (i < third.size() && Boolean.TRUE.equals(third.get(i))) {
          frames.add(WorkKeys.livePanelPath(n, 2));
        }
        m.put("intervalMs", motion.get().intervalMs());
        m.put("easing", "ease-in-out");
      } else {
        panel.putNull("motion");
      }
    }
    return root;
  }

  /** 대사 자막. 컷 시간을 대사 글자 수 비율로 나눠 차례로 보여 준다. 화자는 호칭(없으면 null, 해설). */
  static void captions(
      ArrayNode out, ComicScript.Panel p, java.util.Map<String, String> names, int durationMs) {
    List<ComicScript.Line> lines =
        p.lines() == null
            ? List.of()
            : p.lines().stream().filter(l -> l.text() != null && !l.text().isBlank()).toList();
    if (lines.isEmpty()) {
      return;
    }
    int total = lines.stream().mapToInt(l -> l.text().strip().length()).sum();
    int usable = durationMs - CAPTION_LEAD_MS - CAPTION_GAP_MS * (lines.size() - 1);
    int start = CAPTION_LEAD_MS;
    for (int k = 0; k < lines.size(); k++) {
      ComicScript.Line l = lines.get(k);
      int len = (int) Math.round((double) usable * l.text().strip().length() / Math.max(1, total));
      int end = k == lines.size() - 1 ? durationMs : Math.min(durationMs, start + len);
      ObjectNode c = out.addObject();
      String speaker = l.speaker() == null ? null : names.get(l.speaker());
      if (speaker == null) {
        c.putNull("speaker");
      } else {
        c.put("speaker", speaker);
      }
      c.put("text", l.text().strip());
      c.put("startMs", start);
      c.put("endMs", end);
      start = Math.min(durationMs, end + CAPTION_GAP_MS);
    }
  }

  /** 대사 글자 수에 맞춘 보여 주는 시간. */
  static int durationMs(ComicScript.Panel p) {
    int chars =
        p.lines() == null
            ? 0
            : p.lines().stream().mapToInt(l -> l.text() == null ? 0 : l.text().length()).sum();
    return Math.max(MIN_MS, Math.min(MAX_MS, MIN_MS + chars * PER_CHAR_MS));
  }

  private static ObjectNode sound(
      JsonMapper json, SoundMatcher.Picked s, double volume, int fadeMs, boolean loop) {
    ObjectNode node = json.createObjectNode();
    node.put("publicId", s.publicId());
    node.put("title", s.title());
    node.put("volume", volume);
    node.put("fadeMs", fadeMs);
    node.put("loop", loop);
    if (s.score() != null) {
      // 소리 설명과 컷 질의의 유사도(1 - 코사인 거리). 소리 거리 한도를 맞출 때 본다
      node.put("score", s.score());
    }
    ObjectNode credit = node.putObject("credit");
    credit.put("author", s.author());
    credit.put("license", s.license());
    credit.put("licenseUrl", s.licenseUrl());
    credit.put("sourceUrl", s.sourceUrl());
    credit.put("originalTitle", s.originalTitle());
    return node;
  }
}
