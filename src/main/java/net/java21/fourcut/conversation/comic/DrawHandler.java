package net.java21.fourcut.conversation.comic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * DRAW: 콘티의 컷 4장을 GPU 워커(SDXL)에 그리게 한다. 워커는 사전 서명 PUT 주소로 PNG를 올린다. 같은 대화는 같은 시드를 써서 캐릭터 모습을 맞춘다.
 * 그림에는 글자를 넣지 않고, 말풍선은 COMPOSE에서 코드로 그린다.
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
    UUID id = context.conversation().id();
    ComicScript script = scenarios.script(id);
    String cast =
        script.characters().stream()
            .map(c -> "person " + c.speaker() + ": " + c.look())
            .collect(Collectors.joining("; "));
    long seed = Math.floorMod(id.getMostSignificantBits(), 1_000_000_000L) + context.attempt();
    ObjectNode payload = json.createObjectNode();
    ArrayNode images = payload.putArray("images");
    for (int i = 0; i < PANELS; i++) {
      ComicScript.Panel panel = script.panels().get(i);
      ObjectNode image = images.addObject();
      image.put("name", panelName(i));
      image.put("prompt", properties.style() + ". " + cast + ". scene: " + panel.scene());
      image.put("negativePrompt", properties.negativePrompt());
      image.put("seed", seed);
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
    UUID id = context.conversation().id();
    for (int i = 0; i < PANELS; i++) {
      if (!uploaded.contains(panelName(i)) || !storage.exists(WorkKeys.panel(id, i + 1))) {
        return new StepOutcome.Retry(panelName(i) + " 그림이 올라오지 않았다");
      }
    }
    return new StepOutcome.Done(WorkKeys.workPrefix(id), Step.COMPOSE);
  }

  static String panelName(int index) {
    return "panel-" + (index + 1);
  }

  static List<String> panelNames() {
    return List.of(panelName(0), panelName(1), panelName(2), panelName(3));
  }
}
