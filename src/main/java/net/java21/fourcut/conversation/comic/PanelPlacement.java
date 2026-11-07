package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 말풍선 자리를 그려진 그림에 맞춘다. 그림 모델은 정해 준 자리(왼쪽·오른쪽)를 자주 어겨, 할아버지와 손주가 바뀌어 서면 말풍선 꼬리가 다른 사람을 가리켰다. 누가 말하는
 * 컷을 외부 LLM(그림을 읽는 모델)에 보여 주고 인물이 실제로 선 쪽과 얼굴 위치를 받는다. 말풍선은 얼굴 위치에 맞춰 놓고 꼬리가 그 얼굴을 가리킨다(정해 둔 위·아래
 * 자리에 붙이면 얼굴을 가리거나 엉뚱한 곳을 가리켰다). 부를 수 없거나 답이 이상하면 정해 둔 자리 그대로다.
 */
@Component
class PanelPlacement {

  private static final Logger log = LoggerFactory.getLogger(PanelPlacement.class);

  /** 보내는 그림 한 변(픽셀). 좌우만 보면 되므로 작게 보낸다. */
  static final int SIZE = 384;

  static final String SYSTEM =
      "너는 만화 컷에서 인물이 선 자리를 확인한다. 컷마다 인물 설명을 보고, 그 인물이 그림의 왼쪽(left), 오른쪽(right), 가운데(center)"
          + " 중 어디에 있는지, 그림에 없으면 absent로 답한다. 나이·성별·머리·옷으로 가린다. 두 사람을 같은 쪽으로 답하지 않는다."
          + " 그림에 있는 인물은 얼굴 가운데 위치를 그림 폭·높이에 대한 백분율 x, y(왼쪽 위가 0, 오른쪽 아래가 100)로,"
          + " 얼굴 높이를 그림 높이에 대한 백분율 size로 답한다. 인물 참조 그림이 먼저 오면 컷 속 인물을 참조의 얼굴·머리·옷 색과 맞춰 가린다."
          + " 한 얼굴을 두 사람에게 주지 않는다. 그림에 한 사람만 보이면 그 사람 하나만 답하고 나머지는 absent다.";

  static final String SCHEMA =
      """
      {"type":"object","properties":{"panels":{"type":"array","items":{"type":"object",\
      "properties":{"panel":{"type":"integer"},"people":{"type":"array","items":{"type":"object",\
      "properties":{"speaker":{"type":"string"},"side":{"type":"string",\
      "enum":["left","right","center","absent"]},"x":{"type":"number"},"y":{"type":"number"},\
      "size":{"type":"number"}},"required":["speaker","side"]}}},\
      "required":["panel","people"]}}},"required":["panels"]}""";

  private final DirectLlm llm;
  private final JsonMapper json;

  /** {@code llm}은 지연 주입한다(외부 LLM 실행기가 파이프라인을 거쳐 다시 닿는 순환을 끊는다). */
  PanelPlacement(@Lazy DirectLlm llm, JsonMapper json) {
    this.llm = llm;
    this.json = json;
  }

  /**
   * 그림에서 본 얼굴. 그림 폭·높이에 대한 비율(0~1).
   *
   * @param x 얼굴 가운데 가로 위치
   * @param y 얼굴 가운데 세로 위치
   * @param size 얼굴 높이
   */
  record Head(double x, double y, double size) {}

  /**
   * 컷별 자리와 얼굴.
   *
   * @param stages 컷별 인물 자리(확인한 컷은 그림에서 본 쪽)
   * @param heads 컷별 인물 얼굴(본 인물만)
   */
  record Spots(List<Map<String, Staging.Side>> stages, List<Map<String, Head>> heads) {}

  /** 컷별 자리. 확인한 컷은 그림에서 본 자리로, 나머지는 정해 둔 자리 그대로. */
  List<Map<String, Staging.Side>> check(
      ComicScript script, List<BufferedImage> panels, List<Map<String, Staging.Side>> planned) {
    return locate(script, panels, planned).stages();
  }

  /** 참조 그림 한 변(픽셀). */
  static final int REF_SIZE = 256;

  /**
   * 한 얼굴을 두 사람이 가리킨 자리에 남기는 표시. 누구 얼굴인지 모르므로 두 사람 다 얼굴을 빼고, 말풍선이 그 얼굴을 피하게만 둔다(할머니만 그려진 컷에서 손녀 말풍선
   * 꼬리도 할머니를 가리켰다).
   */
  static final String UNKNOWN = "?";

  /** 컷별 자리와 얼굴. 얼굴을 모르는 컷은 빈 맵이다. */
  Spots locate(
      ComicScript script, List<BufferedImage> panels, List<Map<String, Staging.Side>> planned) {
    return locate(script, panels, planned, Map.of());
  }

  /**
   * 컷별 자리와 얼굴. refs(화자 → 참조 그림 PNG)가 있으면 컷보다 먼저 보여 주어, 인물 설명 글만으로 가리던 것을 그림을 그릴 때 쓴 참조와 맞춰 가린다. 그림
   * 모델이 두 사람을 다 노인으로 그리는 등 설명과 다르게 그리면 글만으로는 말풍선이 바뀌었다(편지 만화 2·6컷).
   */
  Spots locate(
      ComicScript script,
      List<BufferedImage> panels,
      List<Map<String, Staging.Side>> planned,
      Map<String, byte[]> refs) {
    List<Map<String, Head>> none = new ArrayList<>();
    planned.forEach(p -> none.add(Map.of()));
    Spots fallback = new Spots(planned, none);
    List<Integer> asked = asked(script, planned, panels.size());
    if (asked.isEmpty() || !llm.available()) {
      return fallback;
    }
    Map<String, byte[]> shown = shown(refs, planned, asked);
    List<byte[]> images = new ArrayList<>(shown.values());
    asked.forEach(i -> images.add(png(panels.get(i), SIZE)));
    Optional<String> answer;
    try {
      answer =
          llm.see(
              SYSTEM, refPrompt(script, shown) + prompt(script, planned, asked), SCHEMA, images);
    } catch (RuntimeException e) {
      log.warn("말풍선 자리 확인 실패: {}", e.toString());
      return fallback;
    }
    if (answer.isEmpty()) {
      return fallback;
    }
    Map<Integer, Map<String, String>> seen = read(answer.get(), asked);
    Map<Integer, Map<String, Head>> faces = heads(answer.get(), asked);
    List<Map<String, Staging.Side>> out = new ArrayList<>(planned);
    List<Map<String, Head>> heads = new ArrayList<>(none);
    for (Map.Entry<Integer, Map<String, String>> e : seen.entrySet()) {
      int i = e.getKey();
      Map<String, Staging.Side> fixed = fix(planned.get(i), e.getValue());
      if (!fixed.equals(planned.get(i))) {
        log.info("말풍선 자리 고침: 컷 {} {} -> {}", i + 1, planned.get(i), fixed);
      }
      out.set(i, fixed);
      heads.set(i, faces.getOrDefault(i, Map.of()));
    }
    return new Spots(out, heads);
  }

  /** 확인할 컷(0부터): 컷에 선 인물 가운데 누가 말하는 컷. 혼자 나오는 컷도 얼굴 위치를 보려고 묻는다. */
  static List<Integer> asked(
      ComicScript script, List<Map<String, Staging.Side>> planned, int count) {
    List<Integer> out = new ArrayList<>();
    for (int i = 0; i < Math.min(count, planned.size()); i++) {
      Map<String, Staging.Side> stage = planned.get(i);
      List<ComicScript.Line> lines = script.panels().get(i).lines();
      boolean talking =
          lines != null && lines.stream().anyMatch(l -> stage.containsKey(l.speaker()));
      if (!stage.isEmpty() && talking) {
        out.add(i);
      }
    }
    return out;
  }

  /** 보여 줄 참조: 확인할 컷에 선 인물 가운데 참조 그림을 읽을 수 있는 인물만, 줄인 PNG로. */
  static Map<String, byte[]> shown(
      Map<String, byte[]> refs, List<Map<String, Staging.Side>> planned, List<Integer> asked) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    for (int i : asked) {
      for (String who : planned.get(i).keySet()) {
        byte[] raw = refs.get(who);
        if (raw == null || out.containsKey(who)) {
          continue;
        }
        BufferedImage image;
        try {
          image = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (IOException e) {
          image = null;
        }
        if (image != null) {
          out.put(who, png(image, REF_SIZE));
        }
      }
    }
    return out;
  }

  /** 참조 그림 설명. 참조가 없으면 빈 문자열이다. */
  static String refPrompt(ComicScript script, Map<String, byte[]> shown) {
    if (shown.isEmpty()) {
      return "";
    }
    Map<String, String> calls = new HashMap<>();
    script.characters().forEach(c -> calls.put(c.speaker(), c.callName()));
    StringBuilder sb = new StringBuilder("처음 ").append(shown.size()).append("장은 인물 참조 그림이다.\n");
    int n = 1;
    for (String who : shown.keySet()) {
      sb.append("- 참조 ")
          .append(n++)
          .append(": ")
          .append(who)
          .append(" (")
          .append(calls.getOrDefault(who, who))
          .append(")\n");
    }
    sb.append("그 뒤가 컷 그림이다. 그림 번호는 컷 그림만 센다.\n\n");
    return sb.toString();
  }

  /** 그림 순서대로 컷 번호와 인물 설명. 인물은 등장인물 표시(A, B…)로 답하게 한다. */
  static String prompt(
      ComicScript script, List<Map<String, Staging.Side>> planned, List<Integer> asked) {
    Map<String, ComicScript.Character> cast = new HashMap<>();
    script.characters().forEach(c -> cast.put(c.speaker(), c));
    StringBuilder sb = new StringBuilder("그림은 아래 컷 순서대로 붙어 있다.\n");
    for (int n = 0; n < asked.size(); n++) {
      int i = asked.get(n);
      sb.append("\n그림 ").append(n + 1).append(" (panel ").append(i + 1).append(")\n");
      for (String who : planned.get(i).keySet()) {
        ComicScript.Character c = cast.get(who);
        String look = c == null || c.look() == null ? "" : c.look().strip();
        String call = c == null ? who : c.callName();
        sb.append("- ")
            .append(who)
            .append(": ")
            .append(call)
            .append(", ")
            .append(look)
            .append('\n');
      }
    }
    sb.append("\npanel에는 괄호 안 panel 번호를 그대로 쓴다.");
    return sb.toString();
  }

  /** 답에서 컷별 인물 자리. 물은 컷이 아니면 버린다. */
  Map<Integer, Map<String, String>> read(String answer, List<Integer> asked) {
    Map<Integer, Map<String, String>> out = new LinkedHashMap<>();
    JsonNode root;
    try {
      root = json.readTree(answer);
    } catch (RuntimeException e) {
      log.warn("말풍선 자리 답을 읽지 못했다: {}", e.toString());
      return out;
    }
    for (JsonNode p : root.path("panels")) {
      int i = p.path("panel").asInt(0) - 1;
      if (!asked.contains(i)) {
        continue;
      }
      Map<String, String> sides = new HashMap<>();
      for (JsonNode who : p.path("people")) {
        sides.put(who.path("speaker").asString(""), who.path("side").asString(""));
      }
      out.put(i, sides);
    }
    return out;
  }

  /** 답에서 컷별 얼굴. 값이 그림 밖이거나 없으면 그 인물은 뺀다. */
  Map<Integer, Map<String, Head>> heads(String answer, List<Integer> asked) {
    Map<Integer, Map<String, Head>> out = new LinkedHashMap<>();
    JsonNode root;
    try {
      root = json.readTree(answer);
    } catch (RuntimeException e) {
      return out;
    }
    for (JsonNode p : root.path("panels")) {
      int i = p.path("panel").asInt(0) - 1;
      if (!asked.contains(i)) {
        continue;
      }
      Map<String, Head> faces = new HashMap<>();
      for (JsonNode who : p.path("people")) {
        head(who).ifPresent(h -> faces.put(who.path("speaker").asString(""), h));
      }
      out.put(i, distinct(faces));
    }
    return out;
  }

  /**
   * 한 얼굴을 두 사람에게 준 답을 거른다. 두 얼굴 가운데가 큰 얼굴 높이의 절반보다 가까우면 같은 얼굴로 본다. 누구 얼굴인지 모르니 두 사람 다 빼고 그 얼굴은
   * {@link #UNKNOWN}으로 남겨 말풍선이 가리지 않게 한다.
   */
  static Map<String, Head> distinct(Map<String, Head> faces) {
    List<String> names = new ArrayList<>(faces.keySet());
    java.util.Set<String> clash = new java.util.HashSet<>();
    for (int a = 0; a < names.size(); a++) {
      for (int b = a + 1; b < names.size(); b++) {
        Head p = faces.get(names.get(a));
        Head q = faces.get(names.get(b));
        if (Math.hypot(p.x() - q.x(), p.y() - q.y()) < Math.max(p.size(), q.size()) / 2) {
          clash.add(names.get(a));
          clash.add(names.get(b));
        }
      }
    }
    if (clash.isEmpty()) {
      return faces;
    }
    log.info("한 얼굴을 두 사람에게 준 답이라 얼굴을 뺀다: {}", clash);
    Map<String, Head> out = new HashMap<>();
    faces.forEach(
        (who, h) -> {
          if (clash.contains(who)) {
            out.putIfAbsent(UNKNOWN, h);
          } else {
            out.put(who, h);
          }
        });
    return out;
  }

  /** 인물 하나의 얼굴. absent거나 값이 0~100 밖이면 없다. size는 2~80만 믿는다. */
  static Optional<Head> head(JsonNode who) {
    if ("absent".equals(who.path("side").asString(""))
        || !who.path("x").isNumber()
        || !who.path("y").isNumber()
        || !who.path("size").isNumber()) {
      return Optional.empty();
    }
    double x = who.path("x").asDouble();
    double y = who.path("y").asDouble();
    double size = who.path("size").asDouble();
    if (x < 0 || x > 100 || y < 0 || y > 100 || size < 2 || size > 80) {
      return Optional.empty();
    }
    return Optional.of(new Head(x / 100, y / 100, size / 100));
  }

  /**
   * 정해 둔 자리를 본 자리로 고친다. 보인 인물은 본 쪽에, 안 보인 인물은 정해 둔 쪽이 보인 인물과 겹칠 때만 반대쪽에 둔다. 두 사람이 같은 쪽(왼쪽·오른쪽)이라는
   * 답은 믿지 않고 정해 둔 자리를 쓴다.
   */
  static Map<String, Staging.Side> fix(
      Map<String, Staging.Side> planned, Map<String, String> seen) {
    Map<String, Staging.Side> present = new LinkedHashMap<>();
    for (String who : planned.keySet()) {
      Staging.Side side = side(seen.get(who));
      if (side != null) {
        present.put(who, side);
      }
    }
    long left = present.values().stream().filter(s -> s == Staging.Side.LEFT).count();
    long right = present.values().stream().filter(s -> s == Staging.Side.RIGHT).count();
    if (present.isEmpty() || left > 1 || right > 1) {
      return planned;
    }
    Map<String, Staging.Side> out = new LinkedHashMap<>();
    for (Map.Entry<String, Staging.Side> e : planned.entrySet()) {
      Staging.Side side = present.get(e.getKey());
      if (side == null) {
        side = e.getValue();
        if (side != Staging.Side.CENTER && present.containsValue(side)) {
          side = side == Staging.Side.LEFT ? Staging.Side.RIGHT : Staging.Side.LEFT;
        }
      }
      out.put(e.getKey(), side);
    }
    return out;
  }

  private static Staging.Side side(String seen) {
    if (seen == null) {
      return null;
    }
    return switch (seen) {
      case "left" -> Staging.Side.LEFT;
      case "right" -> Staging.Side.RIGHT;
      case "center" -> Staging.Side.CENTER;
      default -> null;
    };
  }

  private static byte[] png(BufferedImage image, int size) {
    BufferedImage small = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = small.createGraphics();
    try {
      g.drawImage(image.getScaledInstance(size, size, Image.SCALE_SMOOTH), 0, 0, null);
    } finally {
      g.dispose();
    }
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(small, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
