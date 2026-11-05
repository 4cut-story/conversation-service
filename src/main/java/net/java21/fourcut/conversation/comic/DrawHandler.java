package net.java21.fourcut.conversation.comic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.character.CharacterLibrary;
import net.java21.fourcut.conversation.character.CharacterRef;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Concept;
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
 *
 * <p>캐릭터 DB(REQ-018)에서 찾은 인물은 외모 문구를 캐릭터의 문구로 바꾸고, 그 인물이 나오는 컷에 IP-Adapter 참조({@code references})를
 * 붙인다. 워커는 참조 그림의 embedding이 있으면 받아 쓰고, 없으면 참조 그림으로 계산해 올린다.
 */
@Component
class DrawHandler implements StepHandler {

  static final int PANELS = 4;

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComicProperties properties;
  private final JsonMapper json;
  private final CharacterLibrary characters;

  DrawHandler(
      ScenarioStore scenarios,
      ObjectStorage storage,
      ComicProperties properties,
      JsonMapper json,
      CharacterLibrary characters) {
    this.scenarios = scenarios;
    this.storage = storage;
    this.properties = properties;
    this.json = json;
    this.characters = characters;
  }

  @Override
  public Step step() {
    return Step.DRAW;
  }

  @Override
  public StepOutcome start(StepContext context) {
    String id = context.conversation().publicId();
    ComicScript written = scenarios.script(id);
    Map<String, CharacterRef> cast = characters.match(wanted(written), concept(written));
    ComicScript script = withLooks(written, cast);
    long seed = Math.floorMod((long) id.hashCode(), 1_000_000_000L) + context.attempt() * 10L;
    ObjectNode payload = json.createObjectNode();
    ArrayNode images = payload.putArray("images");
    for (int i = 0; i < PANELS; i++) {
      ObjectNode image = images.addObject();
      image.put("name", panelName(i));
      image.put("prompt", prompt(style(script, properties), script, script.panels().get(i)));
      image.put("negativePrompt", negative(script, properties));
      // 네 컷을 같은 시드로 그린다. 컷마다 시드를 달리하면 같은 외모 문구로도 인물 얼굴·옷 색이 컷마다 바뀌었다(GPU PC 3회차 비교).
      // 구도는 컷마다 다른 장면·구도 문구로 달라진다
      image.put("seed", seed);
      image.put("width", properties.imageSize());
      image.put("height", properties.imageSize());
      image.put("uploadPutKey", WorkKeys.panel(id, i + 1));
      ArrayNode refs = references(script, script.panels().get(i), cast);
      if (!refs.isEmpty()) {
        image.set("references", refs);
      }
    }
    ObjectNode info = json.createObjectNode();
    info.put("seed", seed);
    info.put("size", properties.imageSize());
    if (!cast.isEmpty()) {
      ObjectNode chars = info.putObject("characters");
      cast.forEach((speaker, ref) -> chars.put(speaker, ref.publicId()));
    }
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
    characters.embedsUploaded(computedEmbeds(context.jobPayload()));
    return new StepOutcome.Done(WorkKeys.workPrefix(id), Step.COMPOSE);
  }

  /**
   * SDXL 프롬프트. CLIP은 앞 77토큰만 읽으므로 짧은 그림체 → 구도 → 인원 → 장면 순으로 둔다. 장면의 {A}, {B}는 그 인물의 외모로 바꾸고, 두 명
   * 이상이면 "two people"(컨셉별 crowd)을 넣는다(넣지 않으면 클로즈업에 한 명만 그리는 일이 잦다). 등장인물에 없는 자리 표시는 "a friend"(컨셉별
   * friend)로 바꾼다. 자리 표시가 없는 예전 콘티는 끝에 인물 외모를 붙인다.
   */
  static String prompt(String style, ComicScript script, ComicScript.Panel panel) {
    Concept concept = Concept.of(script.concept());
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
    scene = UNKNOWN_MARK.matcher(scene).replaceAll(concept.friend());
    String shot = panel.shot() == null ? "" : panel.shot().replace('-', ' ') + " shot, ";
    String count =
        people >= 2
            ? COUNTS.get(Math.min(people, COUNTS.size()) - 1) + " " + concept.crowd() + ", "
            : "";
    return style + ", " + shot + count + scene + cast;
  }

  /** 캐릭터 DB에서 찾을 콘티 인물. */
  static List<CharacterLibrary.Wanted> wanted(ComicScript script) {
    return script.characters().stream()
        .map(c -> new CharacterLibrary.Wanted(c.speaker(), c.displayName(), c.look()))
        .toList();
  }

  static String concept(ComicScript script) {
    return Concept.of(script.concept()).name();
  }

  /** 캐릭터를 찾은 인물은 참조 그림과 맞도록 캐릭터의 외모 문구로 그린다. */
  static ComicScript withLooks(ComicScript script, Map<String, CharacterRef> cast) {
    if (cast.isEmpty()) {
      return script;
    }
    List<ComicScript.Character> looks =
        script.characters().stream()
            .map(
                c ->
                    cast.containsKey(c.speaker())
                        ? new ComicScript.Character(
                            c.speaker(), c.name(), c.persona(), cast.get(c.speaker()).look())
                        : c)
            .toList();
    return new ComicScript(
        script.title(),
        looks,
        script.panels(),
        script.reviewed(),
        script.nameWarnings(),
        script.concept());
  }

  /** IP-Adapter 참조는 한 컷에 두 명까지 (두 명이면 왼쪽·오른쪽 마스크). */
  static final int MAX_REFERENCES = 2;

  /**
   * 컷에 나오는 인물(장면에 처음 나온 순서, 예전 콘티는 등장인물 순서) 중 캐릭터를 찾은 인물의 참조. 두 명 이상 나오는 컷은 먼저 나온 인물을 왼쪽, 다음 인물을
   * 오른쪽에 둔다. 참조가 한 명뿐이어도 자리를 정해 다른 인물까지 그 얼굴로 그리지 않게 한다.
   */
  ArrayNode references(
      ComicScript script, ComicScript.Panel panel, Map<String, CharacterRef> cast) {
    ArrayNode refs = json.createArrayNode();
    if (cast.isEmpty()) {
      return refs;
    }
    List<String> people = people(script, panel);
    for (int i = 0; i < people.size() && i < MAX_REFERENCES; i++) {
      CharacterRef ref = cast.get(people.get(i));
      if (ref == null) {
        continue;
      }
      ObjectNode r = refs.addObject();
      r.put("character", ref.publicId());
      r.put("scale", ref.scale());
      r.put("region", people.size() < 2 ? "full" : i == 0 ? "left" : "right");
      if (ref.embedReady()) {
        r.put("embedGetKey", ref.embedKey());
      } else {
        r.put("imageGetKey", ref.referenceImageKey());
        r.put("embedUploadPutKey", ref.embedTargetKey());
      }
    }
    return refs;
  }

  /** 컷에 나오는 화자 표시, 장면에 처음 나온 순서. */
  static List<String> people(ComicScript script, ComicScript.Panel panel) {
    String scene = panel.scene() == null ? "" : panel.scene();
    Set<String> known = new HashSet<>();
    script.characters().forEach(c -> known.add(c.speaker()));
    if (!scene.contains("{")) {
      return script.characters().stream().map(ComicScript.Character::speaker).toList();
    }
    Set<String> order = new LinkedHashSet<>();
    Matcher m = MARK.matcher(scene);
    while (m.find()) {
      if (known.contains(m.group(1))) {
        order.add(m.group(1));
      }
    }
    return new ArrayList<>(order);
  }

  /** 워커가 이번 작업에서 계산해 올렸어야 할 embedding의 캐릭터. */
  static Set<String> computedEmbeds(JsonNode payload) {
    Set<String> ids = new HashSet<>();
    if (payload == null) {
      return ids;
    }
    for (JsonNode image : payload.path("images")) {
      for (JsonNode ref : image.path("references")) {
        if (ref.has("embedUploadPutKey")) {
          ids.add(ref.path("character").asString());
        }
      }
    }
    return ids;
  }

  private static final Pattern MARK = Pattern.compile("\\{([A-Z])\\}");

  private static final Pattern UNKNOWN_MARK = Pattern.compile("\\{[A-Z]\\}");

  private static final List<String> COUNTS = List.of("one", "two", "three", "four");

  /** 컨셉 그림체. 일상 웹툰(기본·예전 콘티)은 설정의 그림체를 쓴다. */
  static String style(ComicScript script, ComicProperties properties) {
    Concept c = Concept.of(script.concept());
    return c == Concept.DAILY ? properties.style() : c.style();
  }

  static String negative(ComicScript script, ComicProperties properties) {
    String extra = Concept.of(script.concept()).negative();
    return extra.isEmpty()
        ? properties.negativePrompt()
        : properties.negativePrompt() + ", " + extra;
  }

  static String panelName(int index) {
    return "panel-" + (index + 1);
  }

  static List<String> panelNames() {
    return List.of(panelName(0), panelName(1), panelName(2), panelName(3));
  }
}
