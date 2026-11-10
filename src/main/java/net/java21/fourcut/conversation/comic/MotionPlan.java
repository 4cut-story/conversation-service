package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.scenario.Motion;
import net.java21.fourcut.conversation.scenario.Stir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 라이브 모드 움직임 그림을 컷 그림을 보고 정한다 (docs specs/003-live-mode FR-051). 콘티 글만 보고 움직임을 정하면 사무실·식당 컷도 모두
 * "잎·풀·천 흔들기"가 되어, 실내 컷에 나뭇잎이 날리고 커튼만 왔다 갔다 했다. 그림 모델은 "잎을 넣지 마라" 같은 부정문을 무시하고 오히려 그 낱말에 끌려 잎을 그려
 * 넣었다. 그래서 넓은 그림을 다 그린 뒤 그림을 읽는 외부 LLM에 컷들을 보여 주고, 컷마다 그림에 실제로 보이는 것 하나와 그 움직임의 처음·중간·끝 세 장면을 받는다.
 * 세 장면은 그림에 있는 것만 쓴 문장이라 움직임 그림 세 장에 그대로 쓴다. 부를 수 없거나 답이 이상한 컷은 콘티 글로 정한 움직임을 쓴다.
 */
@Component
class MotionPlan {

  private static final Logger log = LoggerFactory.getLogger(MotionPlan.class);

  /** 보내는 그림 폭(픽셀). 16:9 그대로 줄인다. 무엇이 보이는지만 알면 된다. */
  static final int WIDTH = 512;

  static final int HEIGHT = 288;

  /** 한 장면 문장 길이(낱말). 너무 짧으면 어떻게 바뀌는지 모르고, 너무 길면 여러 것을 바꾼다. */
  static final int MIN_WORDS = 4;

  static final int MAX_WORDS = 30;

  static final String SYSTEM =
      "너는 움직이는 만화(라이브 모드)의 배경 움직임을 정한다. 컷마다 넓은 그림 한 장을 더 그려 배경의 한 부분만 조금씩 바꾼 그림 세 장을"
          + " 번갈아 보여 준다. 그림을 보고 컷마다 움직일 것 하나(target)와 그 움직임의 처음·중간·끝 세 장면(states)을 정한다.\n"
          + "- target은 그 그림에 또렷이 보이는 것 하나다. 인물(얼굴·머리카락·몸·옷)과 인물이 들거나 쓰는 물건은 고르지 않는다.\n"
          + "- 그 장소에서 실제로 저절로 움직이는 것을 고른다. 예: 벽시계 바늘, 켜진 모니터·TV 화면, 냄비·찻잔에서 오르는 김, 창밖의 구름이나"
          + " 지나가는 차, 깜빡이는 간판 불빛, 천장 선풍기 날개, 어항의 물방울, 촛불 불꽃, 물결의 반짝임, 내리는 비나 눈, 창밖으로 보이는"
          + " 나뭇가지. 장소와 맞지 않는 것(실내에 날리는 나뭇잎 등)은 고르지 않는다.\n"
          + "- 커튼·천 흔들기는 커튼이 또렷이 보이고 다른 움직일 것이 없을 때만 고른다. 움직일 것이 아무것도 없으면 배경 벽이나 바닥에 비친"
          + " 빛을 고른다(빛 조각이 조금씩 옮겨 가거나 조금씩 따뜻해진다).\n"
          + "- 이웃한 컷은 서로 다른 것을 고른다. 여러 컷이 같은 종류의 움직임을 되풀이하지 않게 한다.\n"
          + "- 움직임은 작고 자연스럽게 한다. 그 물건 안에서만 바뀌고 나머지 그림은 그대로다.\n"
          + "- 컷에 참고 움직임(hint)이 있으면 그것이 그림에 또렷이 보일 때 그것을 쓰고, 보이지 않으면 다른 것을 고른다.\n"
          + "- target은 그림에서 그 물건을 가리키는 짧은 영어 명사구다(예: the wall clock above the door).\n"
          + "- states는 영어 문장 세 개다. 문장마다 그 그림에서 target이 어떻게 보이는지를 현재형으로 쓴다(예: the minute hand of the"
          + " wall clock points to twelve). 세 문장은 차례로 이어지는 움직임이다. 그림에 있는 것만 쓰고, 그림에 없는 물건은 쓰지 않는다."
          + " no, not, without, don't 같은 부정어를 쓰지 않는다. 사람은 쓰지 않는다.";

  static final String SCHEMA =
      """
      {"type":"object","properties":{"panels":{"type":"array","items":{"type":"object",\
      "properties":{"panel":{"type":"integer"},"target":{"type":"string"},\
      "states":{"type":"array","items":{"type":"string"},"minItems":3,"maxItems":3}},\
      "required":["panel","target","states"]}}},"required":["panels"]}""";

  /**
   * 한 컷의 움직임.
   *
   * @param target 움직일 것 (영어 명사구)
   * @param states 처음·중간·끝 세 장면 (영어 문장)
   */
  record Plan(String target, List<String> states) {

    /** n번째(0~2) 그림에서 바꿀 것 (움직임 edit 프롬프트 끝). */
    String change(int n) {
      return "change "
          + target
          + " so that "
          + states.get(n)
          + ". Everything else in the picture stays exactly as it is, with the same colors and"
          + " the same lighting";
    }
  }

  /** 물을 컷. index는 0부터, hint는 콘티 글로 정한 움직임. */
  record Asked(int index, byte[] wide, Motion hint) {}

  private final DirectLlm llm;
  private final JsonMapper json;

  /** {@code llm}은 지연 주입한다(외부 LLM 실행기가 파이프라인을 거쳐 다시 닿는 순환을 끊는다). */
  MotionPlan(@Lazy DirectLlm llm, JsonMapper json) {
    this.llm = llm;
    this.json = json;
  }

  /** 컷마다 움직임을 묻는다. 부를 수 없거나 실패하면 빈 지도(모든 컷이 콘티 글로 정한 움직임). 답이 이상한 컷은 빠진다. */
  Map<Integer, Plan> plan(List<Asked> asked) {
    if (asked.isEmpty() || !llm.available()) {
      return Map.of();
    }
    List<byte[]> images = new ArrayList<>();
    List<Asked> shown = new ArrayList<>();
    for (Asked a : asked) {
      Optional<byte[]> small = small(a.wide());
      if (small.isPresent()) {
        images.add(small.get());
        shown.add(a);
      }
    }
    if (shown.isEmpty()) {
      return Map.of();
    }
    try {
      return llm.see(DirectLlm.MOTION, SYSTEM, prompt(shown), SCHEMA, images)
          .map(answer -> answered(shown, answer))
          .orElse(Map.of());
    } catch (RuntimeException e) {
      log.warn("움직임 그림 정하기 실패: {}", e.toString());
      return Map.of();
    }
  }

  /** 질문 글. 그림은 이 순서대로 앞에 붙는다. */
  static String prompt(List<Asked> shown) {
    StringBuilder out = new StringBuilder("넓은 그림 ").append(shown.size()).append("장을 컷 차례대로 보낸다.\n");
    for (int k = 0; k < shown.size(); k++) {
      Asked a = shown.get(k);
      out.append(k + 1).append("번째 그림: panel ").append(a.index() + 1);
      if (a.hint() != null && a.hint() != Motion.BREEZE) {
        out.append(", hint ").append(a.hint().code());
      }
      out.append('\n');
    }
    return out.append("그림마다 panel 번호와 target, states를 답한다.").toString();
  }

  /** 답을 컷 번호(0부터) → 움직임으로. 인물을 건드리거나 장면이 셋이 아니거나 길이가 맞지 않는 컷은 뺀다. */
  Map<Integer, Plan> answered(List<Asked> shown, String answer) {
    Map<Integer, Plan> out = new LinkedHashMap<>();
    JsonNode root;
    try {
      root = json.readTree(answer);
    } catch (RuntimeException e) {
      log.warn("움직임 그림 답을 읽지 못했다: {}", e.toString());
      return out;
    }
    List<Integer> asked = shown.stream().map(Asked::index).toList();
    for (JsonNode p : root.path("panels")) {
      int index = p.path("panel").asInt(0) - 1;
      if (!asked.contains(index) || out.containsKey(index)) {
        continue;
      }
      String target = clean(p.path("target").asString(""));
      List<String> states = new ArrayList<>();
      p.path("states").forEach(s -> states.add(clean(s.asString(""))));
      if (usable(target, states)) {
        out.put(index, new Plan(target, List.copyOf(states)));
      }
    }
    log.info("움직임 그림 정함: {}", out.values().stream().map(Plan::target).toList());
    return out;
  }

  static boolean usable(String target, List<String> states) {
    if (target.isBlank() || target.split(" ").length > 12 || Stir.touchesPerson(target)) {
      return false;
    }
    if (states.size() != 3 || states.stream().distinct().count() != 3) {
      return false;
    }
    return states.stream()
        .allMatch(
            s -> {
              int words = s.isBlank() ? 0 : s.split(" ").length;
              return words >= MIN_WORDS && words <= MAX_WORDS && !Stir.touchesPerson(s);
            });
  }

  /** 끝의 마침표를 떼고 공백을 하나로. */
  static String clean(String text) {
    return text.strip().replaceAll("[.\\s]+$", "").replaceAll("\\s+", " ");
  }

  /** 보낼 작은 그림(PNG). 읽지 못하면 빈 값. */
  static Optional<byte[]> small(byte[] png) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      if (image == null) {
        return Optional.empty();
      }
      BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      try {
        g.drawImage(image.getScaledInstance(WIDTH, HEIGHT, Image.SCALE_SMOOTH), 0, 0, null);
      } finally {
        g.dispose();
      }
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ImageIO.write(out, "png", bytes);
      return Optional.of(bytes.toByteArray());
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }
}
