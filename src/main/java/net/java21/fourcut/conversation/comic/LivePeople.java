package net.java21.fourcut.conversation.comic;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.character.CharacterLibrary;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 라이브 모드 넓은 그림 속 인물 얼굴 자리 (docs specs/003-live-mode FR-052, common live-mode.yaml LivePerson). 넓은
 * 그림은 컷 그림을 가로로 다시 그린 것이라 인물이 선 자리가 컷 그림과 다르다. 그래서 넓은 그림을 말풍선 자리 확인과 같은 질문(그림을 읽는 LLM)에 한 번 더 보여 얼굴
 * 자리를 받는다. 화면은 이 자리로 인물마다 숨쉬기·몸 흔들림을, 말하는 인물에게 고개 끄덕임을 그린다. 그림은 더 그리지 않는다.
 */
final class LivePeople {

  private static final Logger log = LoggerFactory.getLogger(LivePeople.class);

  /** 컷 하나에 싣는 인물 수(계약 maxItems). */
  static final int MAX = 6;

  private LivePeople() {}

  /**
   * 넓은 그림 컷별 얼굴 자리(화자 → 얼굴). 넓은 그림이 없는 컷은 빈 맵이다. 부를 수 없거나 실패하면 모두 빈 맵(화면은 깊이 지도로 움직인다).
   *
   * @param wide 컷별 넓은 그림
   * @param refs 화자 → 참조 그림 PNG
   * @param ask false면 묻지 않는다(테스트 전용 수동 처리 대화)
   */
  static List<Map<String, PanelPlacement.Head>> find(
      PanelPlacement placement,
      ComicScript script,
      List<Optional<BufferedImage>> wide,
      Map<String, byte[]> refs,
      boolean ask) {
    List<Map<String, PanelPlacement.Head>> none = new ArrayList<>();
    wide.forEach(w -> none.add(Map.of()));
    if (!ask || placement == null || wide.stream().noneMatch(Optional::isPresent)) {
      return none;
    }
    try {
      List<Map<String, Staging.Side>> planned = new ArrayList<>(Staging.panels(script));
      List<BufferedImage> images = new ArrayList<>();
      BufferedImage blank = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
      for (int i = 0; i < planned.size(); i++) {
        Optional<BufferedImage> w = i < wide.size() ? wide.get(i) : Optional.empty();
        images.add(w.orElse(blank));
        if (w.isEmpty()) {
          // 넓은 그림이 없는 컷은 묻지 않는다
          planned.set(i, Map.of());
        }
      }
      List<Map<String, PanelPlacement.Head>> heads =
          placement.locate(script, images, planned, refs).heads();
      List<Map<String, PanelPlacement.Head>> out = new ArrayList<>(none);
      for (int i = 0; i < Math.min(out.size(), heads.size()); i++) {
        if (wide.get(i).isPresent()) {
          out.set(i, heads.get(i));
        }
      }
      log.info(
          "넓은 그림 인물 자리: {}", out.stream().map(m -> m.keySet().stream().sorted().toList()).toList());
      return out;
    } catch (RuntimeException e) {
      log.warn("넓은 그림 인물 자리 확인 실패: {}", e.toString());
      return none;
    }
  }

  /** 재생 정보의 넓은 그림 컷에 인물 자리를 싣는다. 화자는 자막과 같은 호칭으로, 누구인지 모르는 얼굴은 null로 둔다. */
  static void put(
      ObjectNode live, List<Map<String, PanelPlacement.Head>> people, Map<String, String> names) {
    JsonNode panels = live.path("panels");
    for (int i = 0; i < Math.min(panels.size(), people.size()); i++) {
      Map<String, PanelPlacement.Head> faces = people.get(i);
      if (faces == null
          || faces.isEmpty()
          || !"16:9".equals(panels.get(i).path("aspect").asString(""))) {
        continue;
      }
      ArrayNode out = ((ObjectNode) panels.get(i)).putArray("people");
      faces.entrySet().stream()
          .sorted(Map.Entry.comparingByValue((a, b) -> Double.compare(a.x(), b.x())))
          .limit(MAX)
          .forEach(
              e -> {
                ObjectNode p = out.addObject();
                String name = names.get(e.getKey());
                if (name == null) {
                  p.putNull("speaker");
                } else {
                  p.put("speaker", name);
                }
                p.put("x", round(e.getValue().x()));
                p.put("y", round(e.getValue().y()));
                p.put("size", round(e.getValue().size()));
              });
    }
  }

  /** 예전 재생 정보에 실려 있던 인물 자리를 다시 만든 재생 정보로 옮긴다(넓은 그림 컷만). 하나라도 옮겼으면 true. */
  static boolean copy(JsonNode old, ObjectNode live) {
    JsonNode from = old.path("panels");
    JsonNode to = live.path("panels");
    boolean any = false;
    for (int i = 0; i < Math.min(from.size(), to.size()); i++) {
      JsonNode people = from.get(i).path("people");
      if (people.isArray()
          && !people.isEmpty()
          && "16:9".equals(to.get(i).path("aspect").asString(""))) {
        ((ObjectNode) to.get(i)).set("people", people.deepCopy());
        any = true;
      }
    }
    return any;
  }

  /** 화자별 참조 그림(그림을 그릴 때 IP-Adapter에 쓴 것). 캐릭터를 못 찾은 인물은 없다. */
  static Map<String, byte[]> references(CharacterLibrary characters, ComicScript script) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    if (script.characters() == null || characters == null) {
      return out;
    }
    for (ComicScript.Character c : script.characters()) {
      characters.referenceImage(c.lookId()).ifPresent(png -> out.put(c.speaker(), png));
    }
    return out;
  }

  /** 넓은 그림 컷이 있는지. */
  static boolean hasWide(JsonNode live) {
    for (JsonNode p : live.path("panels")) {
      if ("16:9".equals(p.path("aspect").asString(""))) {
        return true;
      }
    }
    return false;
  }

  /** 소수 셋째 자리까지. */
  static double round(double v) {
    return Math.round(Math.min(1, Math.max(0, v)) * 1000) / 1000.0;
  }
}
