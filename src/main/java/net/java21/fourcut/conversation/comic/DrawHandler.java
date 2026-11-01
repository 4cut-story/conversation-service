package net.java21.fourcut.conversation.comic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * DRAW: 콘티의 컷 4장을 GPU 워커(SDXL)에 그리게 한다. 워커는 사전 서명 PUT 주소로 PNG를 올린다. 컷마다 구도와 시드가 다르고, 인물 모습은 외모 문구로
 * 맞춘다. 그림에는 글자를 넣지 않고, 말풍선은 COMPOSE에서 코드로 그린다.
 */
@Component
class DrawHandler implements StepHandler {

  static final int PANELS = 4;

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComicProperties properties;
  private final JsonMapper json;

  DrawHandler(
      ScenarioStore scenarios, ObjectStorage storage, ComicProperties properties, JsonMapper json) {
    this.scenarios = scenarios;
    this.storage = storage;
    this.properties = properties;
    this.json = json;
  }

  @Override
  public Step step() {
    return Step.DRAW;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    ComicScript script = scenarios.script(id);
    long seed = Math.floorMod((long) id.hashCode(), 1_000_000_000L) + context.attempt() * 10L;
    ObjectNode payload = json.createObjectNode();
    ArrayNode images = payload.putArray("images");
    for (int i = 0; i < PANELS; i++) {
      ObjectNode image = images.addObject();
      image.put("name", panelName(i));
      image.put("prompt", prompt(properties.style(), script, script.panels().get(i)));
      image.put("negativePrompt", properties.negativePrompt());
      // 컷마다 시드를 달리해 같은 구도가 반복되지 않게 한다. 인물은 외모 문구로 맞춘다
      image.put("seed", seed + i);
      image.put("width", properties.imageSize());
      image.put("height", properties.imageSize());
      image.put("uploadPutKey", WorkKeys.panel(id, i + 1));
    }
    ObjectNode info = json.createObjectNode();
    info.put("seed", seed);
    info.put("size", properties.imageSize());
    return new StepOutcome.Enqueue(JobType.IMAGE, payload, json.writeValueAsString(info));
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    Set<String> uploaded = new HashSet<>();
    result.path("uploaded").forEach(n -> uploaded.add(n.asString()));
    String id = context.conversation().publicId();
    for (int i = 0; i < PANELS; i++) {
      if (!uploaded.contains(panelName(i)) || !storage.exists(WorkKeys.panel(id, i + 1))) {
        return new StepOutcome.Retry(panelName(i) + " 그림이 올라오지 않았다");
      }
    }
    return new StepOutcome.Done(WorkKeys.workPrefix(id), Step.COMPOSE);
  }

  /**
   * SDXL 프롬프트. CLIP은 앞 77토큰만 읽으므로 짧은 그림체 → 구도 → 인원 → 장면 순으로 둔다. 장면의 {A}, {B}는 그 인물의 외모로 바꾸고, 두 명
   * 이상이면 "two people"을 넣는다(넣지 않으면 클로즈업에 한 명만 그리는 일이 잦다). 등장인물에 없는 자리 표시는 "a friend"로 바꾼다. 자리 표시가 없는
   * 예전 콘티는 끝에 인물 외모를 붙인다.
   */
  static String prompt(String style, ComicScript script, ComicScript.Panel panel) {
    String scene = panel.scene().strip();
    boolean legacy = !scene.contains("{");
    StringBuilder cast = new StringBuilder();
    int people = 0;
    for (ComicScript.Character c : script.characters()) {
      String mark = "{" + c.speaker() + "}";
      if (legacy) {
        cast.append("; ").append(c.look().strip());
      } else if (scene.contains(mark)) {
        people++;
        scene = scene.replace(mark, "(" + c.look().strip() + ")");
      }
    }
    scene = UNKNOWN_MARK.matcher(scene).replaceAll("a friend");
    String shot = panel.shot() == null ? "" : panel.shot().replace('-', ' ') + " shot, ";
    String count = people >= 2 ? COUNTS.get(Math.min(people, COUNTS.size()) - 1) + " people, " : "";
    return style + ", " + shot + count + scene + cast;
  }

  private static final Pattern UNKNOWN_MARK = Pattern.compile("\\{[A-Z]\\}");

  private static final List<String> COUNTS = List.of("one", "two", "three", "four");

  static String panelName(int index) {
    return "panel-" + (index + 1);
  }

  static List<String> panelNames() {
    return List.of(panelName(0), panelName(1), panelName(2), panelName(3));
  }
}
