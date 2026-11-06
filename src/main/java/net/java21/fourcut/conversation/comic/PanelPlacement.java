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
 * 말풍선 자리를 그려진 그림에 맞춘다. 그림 모델은 정해 준 자리(왼쪽·오른쪽)를 자주 어겨, 할아버지와 손주가 바뀌어 서면 말풍선 꼬리가 다른 사람을 가리켰다. 두 명 이상
 * 나오는 컷을 외부 LLM(그림을 읽는 모델)에 보여 주고 인물이 실제로 선 쪽을 받아 말풍선 자리로 쓴다. 부를 수 없거나 답이 이상하면 정해 둔 자리 그대로다.
 */
@Component
class PanelPlacement {

  private static final Logger log = LoggerFactory.getLogger(PanelPlacement.class);

  /** 보내는 그림 한 변(픽셀). 좌우만 보면 되므로 작게 보낸다. */
  static final int SIZE = 384;

  static final String SYSTEM =
      "너는 만화 컷에서 인물이 선 자리를 확인한다. 컷마다 인물 설명을 보고, 그 인물이 그림의 왼쪽(left), 오른쪽(right), 가운데(center)"
          + " 중 어디에 있는지, 그림에 없으면 absent로 답한다. 나이·성별·머리·옷으로 가린다. 두 사람을 같은 쪽으로 답하지 않는다.";

  static final String SCHEMA =
      """
      {"type":"object","properties":{"panels":{"type":"array","items":{"type":"object",\
      "properties":{"panel":{"type":"integer"},"people":{"type":"array","items":{"type":"object",\
      "properties":{"speaker":{"type":"string"},"side":{"type":"string",\
      "enum":["left","right","center","absent"]}},"required":["speaker","side"]}}},\
      "required":["panel","people"]}}},"required":["panels"]}""";

  private final DirectLlm llm;
  private final JsonMapper json;

  /** {@code llm}은 지연 주입한다(외부 LLM 실행기가 파이프라인을 거쳐 다시 닿는 순환을 끊는다). */
  PanelPlacement(@Lazy DirectLlm llm, JsonMapper json) {
    this.llm = llm;
    this.json = json;
  }

  /** 컷별 자리. 확인한 컷은 그림에서 본 자리로, 나머지는 정해 둔 자리 그대로. */
  List<Map<String, Staging.Side>> check(
      ComicScript script, List<BufferedImage> panels, List<Map<String, Staging.Side>> planned) {
    List<Integer> asked = asked(script, planned, panels.size());
    if (asked.isEmpty() || !llm.available()) {
      return planned;
    }
    List<byte[]> images = new ArrayList<>();
    asked.forEach(i -> images.add(png(panels.get(i))));
    Optional<String> answer;
    try {
      answer = llm.see(SYSTEM, prompt(script, planned, asked), SCHEMA, images);
    } catch (RuntimeException e) {
      log.warn("말풍선 자리 확인 실패: {}", e.toString());
      return planned;
    }
    if (answer.isEmpty()) {
      return planned;
    }
    Map<Integer, Map<String, String>> seen = read(answer.get(), asked);
    List<Map<String, Staging.Side>> out = new ArrayList<>(planned);
    for (Map.Entry<Integer, Map<String, String>> e : seen.entrySet()) {
      int i = e.getKey();
      Map<String, Staging.Side> fixed = fix(planned.get(i), e.getValue());
      if (!fixed.equals(planned.get(i))) {
        log.info("말풍선 자리 고침: 컷 {} {} -> {}", i + 1, planned.get(i), fixed);
      }
      out.set(i, fixed);
    }
    return out;
  }

  /** 확인할 컷(0부터): 두 명 이상이 서고, 그중 누가 말하는 컷. */
  static List<Integer> asked(
      ComicScript script, List<Map<String, Staging.Side>> planned, int count) {
    List<Integer> out = new ArrayList<>();
    for (int i = 0; i < Math.min(count, planned.size()); i++) {
      Map<String, Staging.Side> stage = planned.get(i);
      List<ComicScript.Line> lines = script.panels().get(i).lines();
      boolean talking =
          lines != null && lines.stream().anyMatch(l -> stage.containsKey(l.speaker()));
      if (stage.size() >= 2 && talking) {
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
