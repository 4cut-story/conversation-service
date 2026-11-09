package net.java21.fourcut.conversation.scenario;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 콘티 LLM 응답(칸을 나눈 초안)을 콘티 모양으로 펼친다. 인물의 아래옷(bottom)·신발(shoes) 칸은 외모(look) 뒤에 붙이고,
 * 장면(scene)·배경(setting)의 소품 자리표({목검})는 소품 정의(props의 draw)로 바꾼다. LLM이 컷마다 소품을 새로 쓰면 1컷 목검이 2컷 쇠칼로
 * 바뀌었고, 외모에 아래옷·신발이 없으면 컷마다 바지 색이 달라졌다(회차 29: 아래옷·신발을 적으면 컷 사이 옷·소품 유지 43건 중 28건 → 33건).
 */
final class ScriptDraft {

  private static final Pattern MARK = Pattern.compile("\\{([^{}]+)}");
  private static final Pattern SPEAKER = Pattern.compile("[A-Z]");

  private ScriptDraft() {}

  /** 초안을 펼친 새 JSON. 칸이 없는 예전 모양 응답은 그대로 둔다. */
  static JsonNode flatten(JsonNode draft) {
    if (!(draft instanceof ObjectNode node)) {
      return draft;
    }
    ObjectNode out = node.deepCopy();
    for (JsonNode c : out.path("characters")) {
      if (c instanceof ObjectNode character) {
        String look =
            Stream.of("look", "bottom", "shoes")
                .map(k -> character.path(k).asString("").strip())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        character.put("look", look);
        character.remove("bottom");
        character.remove("shoes");
      }
    }
    Map<String, String> props = props(out.path("props"));
    Map<String, String> places = props(out.path("places"));
    out.remove("props");
    out.remove("places");
    for (JsonNode p : out.path("panels")) {
      if (p instanceof ObjectNode panel) {
        panel(panel, props, places);
      }
    }
    return out;
  }

  /**
   * 컷 하나를 펼친다. scene은 action + 장소 이름, setting은 장소 정의 + 더 보일 배경 물건이다. face에는 시선(lookAt)을 붙인다. 시선 항목이
   * 컷의 실패 중 많았다(회차 29 채점 160건 중 시선 134건, 장소 이어짐 127건).
   */
  private static void panel(
      ObjectNode panel, Map<String, String> props, Map<String, String> places) {
    if (panel.has("action")) {
      String place = place(panel.path("place").asString("").strip(), places);
      String name = place.split(",", 2)[0].strip();
      String action = resolve(panel.path("action").asString("").strip(), props);
      panel.put("scene", name.isEmpty() ? action : action + ", " + name);
      String more = resolve(panel.path("setting").asString("").strip(), props);
      panel.put(
          "setting",
          Stream.of(place, more).filter(t -> !t.isEmpty()).collect(Collectors.joining(", ")));
      panel.remove("action");
      panel.remove("place");
    } else {
      for (String field : new String[] {"scene", "setting"}) {
        if (panel.path(field).isString()) {
          panel.put(field, resolve(panel.path(field).asString(), props));
        }
      }
    }
    String scene = panel.path("scene").asString("");
    for (JsonNode f : panel.path("faces")) {
      if (f instanceof ObjectNode face && face.has("lookAt")) {
        String gaze = gaze(face.path("lookAt").asString("").strip(), scene, props);
        String expression = face.path("face").asString("").strip();
        face.put(
            "face",
            Stream.of(expression, gaze)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining(", ")));
        face.remove("lookAt");
      }
    }
  }

  /** 장소 key의 정의. 없는 key면 첫 장소, 장소가 없으면 쓴 말 그대로. */
  private static String place(String key, Map<String, String> places) {
    String draw = places.get(key.toLowerCase(Locale.ROOT));
    if (draw != null) {
      return draw;
    }
    return places.isEmpty() || !key.isEmpty() && !key.matches(".*\\p{IsHangul}.*")
        ? key
        : places.values().iterator().next();
  }

  /** 시선 영어 말. 인물 자리표는 그 사람 쪽, 소품 자리표는 그 소품 이름, 나머지는 쓴 낱말. */
  static String gaze(String lookAt, String scene, Map<String, String> props) {
    if (lookAt.isEmpty()) {
      return "";
    }
    Matcher m = MARK.matcher(lookAt);
    if (m.matches()) {
      String key = m.group(1).strip();
      if (SPEAKER.matcher(key).matches()) {
        return scene.contains("{" + key + "}")
            ? "looking at the other person"
            : "looking off to the side at someone";
      }
      String draw = props.getOrDefault(key.toLowerCase(Locale.ROOT), key);
      return "looking at the " + draw.split(",", 2)[0].strip();
    }
    String word = resolve(lookAt, props).toLowerCase(Locale.ROOT);
    if (word.startsWith("looking")) {
      return word;
    }
    return switch (word) {
      case "down", "up", "away", "back" -> "looking " + word;
      case "sky" -> "looking up at the sky";
      default -> "looking at " + word;
    };
  }

  /** 소품 이름(소문자) → 그림용 영어 정의. */
  static Map<String, String> props(JsonNode list) {
    Map<String, String> props = new LinkedHashMap<>();
    for (JsonNode p : list) {
      String key = p.path("key").asString("").strip();
      String draw = p.path("draw").asString("").strip();
      if (!key.isEmpty() && !draw.isEmpty() && !SPEAKER.matcher(key).matches()) {
        props.putIfAbsent(key.toLowerCase(Locale.ROOT), draw);
      }
    }
    return props;
  }

  /** 자리표를 바꾼다. 인물 자리표 {A}는 그대로 두고, 정의가 없는 자리표는 괄호만 벗긴다. */
  static String resolve(String text, Map<String, String> props) {
    Matcher m = MARK.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String key = m.group(1).strip();
      String with =
          SPEAKER.matcher(key).matches()
              ? "{" + key + "}"
              : props.getOrDefault(key.toLowerCase(Locale.ROOT), key);
      m.appendReplacement(sb, Matcher.quoteReplacement(with));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
