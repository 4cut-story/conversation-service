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
 * DRAW: 콘티의 컷(4~10장)을 GPU 워커(SDXL)에 그리게 한다. 워커는 사전 서명 PUT 주소로 PNG를 올린다. 컷마다 구도와 시드가 다르고, 인물 모습은 외모
 * 문구로 맞춘다. 그림에는 글자를 넣지 않고, 말풍선은 COMPOSE에서 코드로 그린다.
 *
 * <p>캐릭터 DB(REQ-018)에서 찾은 인물은 외모 문구를 캐릭터의 문구로 바꾸고, 그 인물이 나오는 컷에 IP-Adapter 참조({@code references})를
 * 붙인다. 워커는 참조 그림의 embedding이 있으면 받아 쓰고, 없으면 참조 그림으로 계산해 올린다. 찾은 인물의 가명은 캐릭터 이름으로 바꿔 콘티에 다시 저장해,
 * 말풍선과 제목에도 캐릭터 이름이 나오게 한다.
 */
@Component
class DrawHandler implements StepHandler {

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
    ComicScript named = withNames(written.withRoleCalls(), cast);
    if (!named.equals(written)) {
      // 등장인물 소개(COMPOSE가 content-service에 넘긴다)에 캐릭터 이름과 모습을 쓰게 저장한다. 대사·제목은 호칭으로 부른다.
      // 다시 그릴 때는 이 이름으로 같은 캐릭터를 찾는다
      scenarios.saveScript(id, named);
    }
    ComicScript script = adults(withLooks(named, cast));
    List<Map<String, Staging.Side>> stages = Staging.panels(script);
    long seed = Math.floorMod(seedOf(written), 1_000_000_000L) + context.attempt() * 10L;
    ObjectNode payload = json.createObjectNode();
    ArrayNode images = payload.putArray("images");
    for (int i = 0; i < script.panels().size(); i++) {
      ObjectNode image = images.addObject();
      image.put("name", panelName(i));
      image.put(
          "prompt",
          prompt(style(script, properties), script, script.panels().get(i), stages.get(i)));
      image.put("negativePrompt", negative(script, properties));
      ArrayNode refs =
          references(stages.get(i), cast, othersInScene(script, script.panels().get(i)));
      image.put("seed", panelSeed(seed, i, stages.get(i), cast));
      image.put("width", properties.imageSize());
      image.put("height", properties.imageSize());
      image.put("uploadPutKey", WorkKeys.panel(id, i + 1));
      if (!refs.isEmpty()) {
        image.set("references", refs);
        image.set("ipSchedule", ipSchedule(properties));
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
    // 컷 수는 콘티마다 다르다(4~10). 보낸 그림 작업의 컷 수만큼 올라왔는지 본다
    int count =
        context.jobPayload() == null || context.jobPayload().path("images").isEmpty()
            ? scenarios.script(id).panels().size()
            : context.jobPayload().path("images").size();
    for (int i = 0; i < count; i++) {
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
   * friend)로 바꾼다. 자리 표시가 없는 예전 콘티는 끝에 인물 외모를 붙인다. 두 명 이상 나오는 컷은 장면 앞에 인물 줄(왼쪽부터 외모 + 자리)을 두고 장면 문장의
   * 인물은 짧은 이름으로 쓴다({@link #lineup}). IP-Adapter 마스크·말풍선과 같은 자리다({@link Staging}).
   */
  static String prompt(String style, ComicScript script, ComicScript.Panel panel) {
    return prompt(style, script, panel, Staging.panel(script, panel));
  }

  static String prompt(
      String style, ComicScript script, ComicScript.Panel panel, Map<String, Staging.Side> stage) {
    Concept concept = Concept.of(script.concept());
    String scene = panel.scene().strip();
    boolean legacy = !scene.contains("{");
    StringBuilder cast = new StringBuilder();
    int people = 0;
    Map<String, String> looks = new java.util.HashMap<>();
    for (ComicScript.Character c : script.characters()) {
      String mark = "{" + c.speaker() + "}";
      if (legacy) {
        cast.append("; ").append(c.look().strip());
      } else if (scene.contains(mark)) {
        people++;
        looks.put(c.speaker(), c.look().strip());
      }
    }
    String lineup = "";
    Map<String, String> names = new java.util.HashMap<>();
    if (people >= 2 && stage.size() >= 2) {
      lineup = lineup(stage, looks);
      names = heads(stage, looks);
      for (Map.Entry<String, String> e : names.entrySet()) {
        scene = scene.replace("{" + e.getKey() + "}", e.getValue());
      }
    } else {
      for (Map.Entry<String, String> e : looks.entrySet()) {
        scene = scene.replace("{" + e.getKey() + "}", "(" + e.getValue() + ")");
      }
    }
    String detail = faces(panel, looks.keySet(), names) + setting(panel);
    scene = UNKNOWN_MARK.matcher(scene).replaceAll(concept.friend());
    // 두 명 이상인 close-up은 한 사람 얼굴만 크게 그려 나머지가 빠지고 두 외모가 한 사람에 섞였다(mystery-02 3컷 등).
    // 여럿이 나오는 컷은 medium으로 그린다.
    String kind = people >= 2 && "close-up".equals(panel.shot()) ? "medium" : panel.shot();
    String shot = kind == null ? "" : kind.replace('-', ' ') + " shot, ";
    String who = people >= 2 && stage.size() >= 2 ? who(stage, looks, concept) : null;
    String count =
        people >= 2
            ? (who != null
                    ? who
                    : COUNTS.get(Math.min(people, COUNTS.size()) - 1) + " " + concept.crowd())
                + ", "
                + SIDE_BY_SIDE
                + ", "
            : "";
    return style + ", " + QUALITY + ", " + SINGLE + ", " + shot + count + lineup + scene + detail
        + cast;
  }

  /**
   * 콘티의 인물별 표정·몸짓. 두 명 이상이면 장면 문장과 같은 짧은 이름("the old woman laughing")으로, 혼자면 표정만 붙인다. 긴 프롬프트는 워커가
   * 75토큰 조각으로 나눠 읽으므로 콘티 내용을 줄이지 않고 넘긴다.
   */
  static String faces(ComicScript.Panel panel, Set<String> inScene, Map<String, String> names) {
    if (panel.faces() == null) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    for (ComicScript.Face f : panel.faces()) {
      if (f == null || f.face() == null || f.face().isBlank() || !inScene.contains(f.speaker())) {
        continue;
      }
      out.append(", ");
      String who = names.get(f.speaker());
      if (who != null) {
        out.append(who).append(' ');
      }
      out.append(f.face().strip());
    }
    return out.toString();
  }

  /** 콘티의 배경(장소와 배경 물건). */
  static String setting(ComicScript.Panel panel) {
    String s = panel.setting();
    return s == null || s.isBlank() ? "" : ", " + s.strip();
  }

  /** 동물 컨셉 외모 첫 마디로 쓰는 동물(Concept.ANIMALS lookRule). */
  private static final Pattern ANIMAL =
      Pattern.compile("rabbit|deer|fox|bear|cat|dog|squirrel|panda");

  private static final Pattern PERSON =
      Pattern.compile("(?:(young|old|little|tall adult) )?(woman|man|girl|boy)");

  /**
   * 사람 컨셉에서 인물 모두의 외모 첫 마디가 나이·성별이면 "two people" 대신 그 사람들로 센다("two young women", "a young woman and
   * an old man", 왼쪽부터). "two people"은 남녀 한 쌍으로 그리는 버릇이 있어, 여자 둘이 나오는 컷에서 한 명이 남자로 그려졌다(office-11,
   * office-15). 동물 컨셉은 종으로 센다("a squirrel and a rabbit"). "two animal characters"만 주면 두 인물이 같은 종으로
   * 섞였다 (office-12, office-20, moving-20). 셀 수 없으면 null.
   */
  static String who(Map<String, Staging.Side> stage, Map<String, String> looks, Concept concept) {
    boolean animals = concept == Concept.ANIMALS;
    if (!animals && !"people".equals(concept.crowd())) {
      return null;
    }
    List<String> heads = new ArrayList<>();
    for (Staging.Side side : List.of(Staging.Side.LEFT, Staging.Side.CENTER, Staging.Side.RIGHT)) {
      for (Map.Entry<String, Staging.Side> e : stage.entrySet()) {
        if (e.getValue() == side && looks.containsKey(e.getKey())) {
          heads.add(looks.get(e.getKey()).split(",")[0].strip().toLowerCase(java.util.Locale.ROOT));
        }
      }
    }
    if (heads.size() < 2 || heads.size() > COUNTS.size()) {
      return null;
    }
    for (String h : heads) {
      if (!(animals ? ANIMAL.matcher(h).matches() : PERSON.matcher(h).matches())) {
        return null;
      }
    }
    String counted = counted(heads);
    // 동물 컨셉은 종을 세어 주어도 사람이 섞이지 않게 컨셉의 crowd 말을 남긴다
    return animals ? counted + ", " + concept.crowd() : counted;
  }

  private static String counted(List<String> heads) {
    if (heads.stream().distinct().count() == 1) {
      String h = heads.get(0);
      String plural =
          h.endsWith("woman")
              ? h.replaceAll("woman$", "women")
              : h.endsWith("man")
                  ? h.replaceAll("man$", "men")
                  : "deer".equals(h) ? h : "fox".equals(h) ? "foxes" : h + "s";
      return COUNTS.get(heads.size() - 1) + " " + plural;
    }
    List<String> each = new ArrayList<>();
    for (String h : heads) {
      each.add((h.matches("^[aeiou].*") ? "an " : "a ") + h);
    }
    return String.join(", ", each.subList(0, each.size() - 1)) + " and " + each.getLast();
  }

  /**
   * 두 명 이상 컷의 인물 줄: 왼쪽 → 가운데 → 오른쪽 순서로 외모와 자리를 먼저 적는다. SDXL은 "on the left" 같은 위치 낱말보다 먼저 나온 인물을 왼쪽에
   * 그리는 경향이 커서, 장면 문장 순서대로 외모를 넣으면 오른쪽 인물이 왼쪽에 그려져 IP-Adapter 마스크(그 자리의 캐릭터 얼굴)가 다른 인물에 씌워졌다.
   */
  static String lineup(Map<String, Staging.Side> stage, Map<String, String> looks) {
    StringBuilder out = new StringBuilder();
    for (Staging.Side side : List.of(Staging.Side.LEFT, Staging.Side.CENTER, Staging.Side.RIGHT)) {
      stage.forEach(
          (who, s) -> {
            if (s == side && looks.containsKey(who)) {
              out.append('(')
                  .append(looks.get(who))
                  .append(')')
                  .append(PLACE.get(side))
                  .append(", ");
            }
          });
    }
    return out.toString();
  }

  /**
   * 장면 문장에서 인물 표시를 짧은 이름(외모 첫 마디, "the old woman")으로 바꾼다. 외모는 인물 줄에 한 번만 적어 77토큰을 아낀다. 첫 마디가 같은 인물이
   * 있으면 자리를 붙인다("the woman on the left").
   */
  static Map<String, String> heads(Map<String, Staging.Side> stage, Map<String, String> looks) {
    Map<String, String> head = new java.util.LinkedHashMap<>();
    looks.forEach((who, look) -> head.put(who, look.split(",")[0].strip()));
    Map<String, String> out = new java.util.LinkedHashMap<>();
    head.forEach(
        (who, h) -> {
          long same = head.values().stream().filter(h::equalsIgnoreCase).count();
          Staging.Side side = stage.get(who);
          String place = same > 1 && side != null ? PLACE.get(side) : "";
          out.put(who, "the " + h + place);
        });
    return out;
  }

  private static final Map<Staging.Side, String> PLACE =
      Map.of(
          Staging.Side.LEFT,
          " on the left",
          Staging.Side.CENTER,
          " in the middle",
          Staging.Side.RIGHT,
          " on the right");

  /**
   * 캐릭터 DB에서 찾을 콘티 인물. 가명은 이름으로 찾지 않는다. 가명 24개 중 4개(유진, 보람, 도담, 은솔)가 DB 캐릭터 이름과 같아서, 그 가명을 받은 인물은
   * 장면과 상관없이 우체부·형사 모습으로 그려졌다.
   */
  static List<CharacterLibrary.Wanted> wanted(ComicScript script) {
    return script.characters().stream()
        .map(
            c ->
                new CharacterLibrary.Wanted(
                    c.speaker(),
                    ComicScript.pseudonym(c.displayName()) ? null : c.displayName(),
                    c.look()))
        .toList();
  }

  static String concept(ComicScript script) {
    return Concept.of(script.concept()).name();
  }

  /**
   * 캐릭터를 찾은 인물은 가명 대신 캐릭터 이름을 쓰고 그 캐릭터 모습 ID를 남긴다(만화 아래 등장인물 소개). 대사·제목에 남은 가명도 바꾸지만, 대사·제목은 먼저
   * 호칭으로 바꿔 두어 이름이 나오지 않는다.
   */
  static ComicScript withNames(ComicScript script, Map<String, CharacterRef> cast) {
    Map<String, String> names = new java.util.LinkedHashMap<>();
    cast.forEach((speaker, ref) -> names.put(speaker, ref.name()));
    ComicScript named = script.withCharacterNames(names);
    if (cast.isEmpty() || named.characters() == null) {
      return named;
    }
    List<ComicScript.Character> looked =
        named.characters().stream()
            .map(
                c ->
                    cast.containsKey(c.speaker())
                        ? c.withLookId(cast.get(c.speaker()).publicId())
                        : c)
            .toList();
    return looked.equals(named.characters()) ? named : named.withCharacters(looked);
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
                        ? c.withLook(
                            CharacterLibrary.drawLook(cast.get(c.speaker()).look(), c.look()))
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

  private static final Pattern CHILD_HEAD =
      Pattern.compile("(?:little |young )?(?:girl|boy)|child|kid");

  private static final Pattern BARE_ADULT = Pattern.compile("(?i)^(woman|man)(?=,|$)");

  /**
   * 아이가 나오는 만화에서 나이 없이 "woman", "man"으로 시작하는 어른은 "tall adult woman"으로 그린다. 그림책 그림체("children's
   * picture book, simple shapes")와 아이 참조 그림이 함께 들어가면 엄마·아빠까지 아이 얼굴·키로 그려졌다(시험 8회 햄스터 만화). 나이가 적힌
   * 어른("old woman", "young man")은 그대로 둔다.
   */
  static ComicScript adults(ComicScript script) {
    if (script.characters() == null) {
      return script;
    }
    boolean child =
        script.characters().stream()
            .anyMatch(
                c ->
                    c.look() != null
                        && CHILD_HEAD
                            .matcher(
                                c.look()
                                    .split(",", 2)[0]
                                    .strip()
                                    .toLowerCase(java.util.Locale.ROOT))
                            .matches());
    if (!child) {
      return script;
    }
    List<ComicScript.Character> grown =
        script.characters().stream()
            .map(
                c -> {
                  if (c.look() == null) {
                    return c;
                  }
                  String look = c.look().strip();
                  Matcher m = BARE_ADULT.matcher(look);
                  if (!m.find()) {
                    return c;
                  }
                  return c.withLook(
                      "tall adult "
                          + m.group(1).toLowerCase(java.util.Locale.ROOT)
                          + look.substring(m.end()));
                })
            .toList();
    return grown.equals(script.characters())
        ? script
        : new ComicScript(
            script.title(),
            grown,
            script.panels(),
            script.reviewed(),
            script.nameWarnings(),
            script.concept());
  }

  /**
   * 컷 시드. 참조 없는 인물이 나오는 컷은 네 컷이 같은 시드다: 컷마다 시드를 달리하면 같은 외모 문구로도 얼굴·옷 색이 바뀌었다(GPU PC 3회차 비교). 컷 인물이
   * 모두 캐릭터 참조(IP-Adapter)로 그려지면 정체성은 참조가 지키므로 컷마다 시드를 바꾼다. 같은 시드면 첫 노이즈가 같아 네 컷이 같은 자세·구도로 나왔다(100편
   * 배치 13편 중 5편).
   */
  static long panelSeed(
      long seed, int index, Map<String, Staging.Side> stage, Map<String, CharacterRef> cast) {
    boolean allRef = !stage.isEmpty() && stage.keySet().stream().allMatch(cast::containsKey);
    return allRef ? seed + PANEL_SEED_STEP * (index + 1) : seed;
  }

  static final long PANEL_SEED_STEP = 7919;

  /** 캐릭터 참조 강도 스케줄. 설정으로 고쳐 GPU PC 워커를 다시 띄우지 않고 조정한다. */
  ObjectNode ipSchedule(ComicProperties p) {
    ObjectNode s = json.createObjectNode();
    s.put("earlyScale", p.ipEarlyScale());
    s.put("earlySteps", p.ipEarlySteps());
    s.put("lateBoost", p.ipLateBoost());
    return s;
  }

  /** IP-Adapter 참조는 한 컷에 두 명까지 (두 명이면 왼쪽·오른쪽 마스크). */
  static final int MAX_REFERENCES = 2;

  /**
   * 컷에 나오는 인물 중 캐릭터를 찾은 인물의 참조. 자리는 {@link Staging}이 정한다: 한 만화 안에서 같은 인물은 늘 같은 쪽에 서고, 혼자면 전체다. 참조가
   * 한 명뿐이어도 자리를 정해 다른 인물까지 그 얼굴로 그리지 않게 한다.
   */
  ArrayNode references(
      ComicScript script, ComicScript.Panel panel, Map<String, CharacterRef> cast) {
    return references(Staging.panel(script, panel), cast);
  }

  ArrayNode references(Map<String, Staging.Side> stage, Map<String, CharacterRef> cast) {
    return references(stage, cast, false);
  }

  /**
   * others: 컷에 캐릭터가 아닌 사람(점원, 기사, "a friend")도 나온다. 그러면 혼자 나오는 캐릭터의 참조를 전체가 아니라 가운데(center)에만 건다.
   * 전체에 걸면 다른 사람까지 그 캐릭터 얼굴로 그려져 한 컷에 같은 인물이 둘 나왔다(100편 배치 13편 중 3편).
   */
  ArrayNode references(
      Map<String, Staging.Side> stage, Map<String, CharacterRef> cast, boolean others) {
    ArrayNode refs = json.createArrayNode();
    if (cast.isEmpty()) {
      return refs;
    }
    int added = 0;
    for (Map.Entry<String, Staging.Side> e : stage.entrySet()) {
      CharacterRef ref = cast.get(e.getKey());
      boolean extra = e.getValue() == Staging.Side.CENTER && stage.size() > 1;
      if (ref == null || extra || added >= MAX_REFERENCES) {
        continue;
      }
      added++;
      ObjectNode r = refs.addObject();
      r.put("character", ref.publicId());
      r.put("scale", ref.scale());
      boolean solo = e.getValue() == Staging.Side.CENTER && stage.size() == 1;
      r.put("region", solo && others ? "center" : REGION.get(e.getValue()));
      if (ref.embedReady()) {
        r.put("embedGetKey", ref.embedKey());
      } else {
        r.put("imageGetKey", ref.referenceImageKey());
        r.put("embedUploadPutKey", ref.embedTargetKey());
      }
    }
    return refs;
  }

  private static final Map<Staging.Side, String> REGION =
      Map.of(Staging.Side.LEFT, "left", Staging.Side.RIGHT, "right", Staging.Side.CENTER, "full");

  /** 장면에 캐릭터 말고 다른 사람이 나오는지: 등장인물에 없는 자리 표시나 사람을 가리키는 낱말. */
  static boolean othersInScene(ComicScript script, ComicScript.Panel panel) {
    String scene = panel.scene() == null ? "" : panel.scene();
    Set<String> known = new HashSet<>();
    script.characters().forEach(c -> known.add(c.speaker()));
    Matcher m = MARK.matcher(scene);
    while (m.find()) {
      if (!known.contains(m.group(1))) {
        return true;
      }
    }
    return OTHERS.matcher(MARK.matcher(scene).replaceAll(" ")).find();
  }

  private static final Pattern OTHERS =
      Pattern.compile(
          "\\b(driver|clerk|cashier|staff|customer|waiter|waitress|friend|stranger|people|crowd"
              + "|villager|passenger|man|woman|men|women|boy|girl|kid|child|children|baby|grandma"
              + "|grandpa|mother|father|mom|dad|teacher|doctor|nurse|officer|guard|seller|vendor"
              + "|shopkeeper|owner|chef|neighbor|someone|everyone)s?\\b",
          Pattern.CASE_INSENSITIVE);

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

  /**
   * 모든 그림체에 붙이는 품질 말. negative를 쓰지 않는 Lightning(guidance 0)에서 선 번짐·뭉개진 얼굴을 줄이려고 긍정 말로 또렷한 선과 얼굴을
   * 건다(만화 품질 개선 계획 2단계).
   */
  static final String QUALITY = "high quality illustration, crisp clean lineart, well-drawn faces";

  /**
   * 한 컷이 한 장면이 되게 하는 말. 컷 안이 네 칸 격자로 그려졌다(무협 '문 앞의 수상한 보물 상자' 1컷). negative의 grid·multiple panels는
   * guidance 0이라 쓰이지 않는다.
   */
  static final String SINGLE = "one single scene";

  /**
   * 여럿이 나오는 컷의 구도 말. 두 사람이 말하는 컷에 한 사람만 그려져 말풍선 자리(왼쪽·오른쪽)와 그림이 어긋났다(그림책 '밤마다 그려진 담벼락 그림의 비밀'
   * 2·4컷). Lightning(guidance 0)은 negative를 읽지 않아 구도는 이 말로만 잡는다.
   */
  static final String SIDE_BY_SIDE = "side by side, both fully visible";

  /** 콘티 내용(제목과 컷 장면)으로 정한 그림 시드. 같은 콘티면 대화 ID가 달라도 같은 시드로 그려 고정 시험 세트로 수정 전후를 비교할 수 있다. */
  static long seedOf(ComicScript script) {
    StringBuilder sb = new StringBuilder(String.valueOf(script.title()));
    if (script.panels() != null) {
      script.panels().forEach(p -> sb.append('\n').append(p.scene()));
    }
    return sb.toString().hashCode();
  }

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
