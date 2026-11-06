package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
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
          + " 얼굴 높이를 그림 높이에 대한 백분율 size로 답한다.";

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

  /** 컷별 자리와 얼굴. 얼굴을 모르는 컷은 빈 맵이다. */
  Spots locate(
      ComicScript script, List<BufferedImage> panels, List<Map<String, Staging.Side>> planned) {
    List<Map<String, Head>> none = new ArrayList<>();
    planned.forEach(p -> none.add(Map.of()));
    Spots fallback = new Spots(planned, none);
    List<Integer> asked = asked(script, planned, panels.size());
    if (asked.isEmpty() || !llm.available()) {
      return fallback;
    }
    List<byte[]> images = new ArrayList<>();
    asked.forEach(i -> images.add(png(panels.get(i))));
    Optional<String> answer;
    try {
      answer = llm.see(SYSTEM, prompt(script, planned, asked), SCHEMA, images);
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
      out.put(i, faces);
    }
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

  private static byte[] png(BufferedImage image) {
    BufferedImage small = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = small.createGraphics();
    try {
      g.drawImage(image.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH), 0, 0, null);
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
