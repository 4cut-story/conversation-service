package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.character.CharacterLibrary;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Concept;
import net.java21.fourcut.conversation.scenario.Dialect;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** COMPOSE: 컷(4~10장)에 말풍선을 그려 세로 만화 한 장과 썸네일을 만들고, content-service 만화함에 등록한다. 서버에서 실행한다. */
@Component
class ComposeHandler implements StepHandler {

  private static final Logger log = LoggerFactory.getLogger(ComposeHandler.class);

  private static final String PNG = "image/png";

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComicComposer composer;
  private final ContentClient content;
  private final PanelPlacement placement;
  private final CharacterLibrary characters;
  private final JsonMapper json;
  private final Production production;
  private final SoundMatcher sounds;

  ComposeHandler(
      ScenarioStore scenarios,
      ObjectStorage storage,
      ComicComposer composer,
      ContentClient content,
      PanelPlacement placement,
      CharacterLibrary characters,
      JsonMapper json,
      Production production,
      SoundMatcher sounds) {
    this.sounds = sounds;
    this.json = json;
    this.production = production;
    this.scenarios = scenarios;
    this.storage = storage;
    this.composer = composer;
    this.content = content;
    this.placement = placement;
    this.characters = characters;
  }

  @Override
  public Step step() {
    return Step.COMPOSE;
  }

  @Override
  public StepOutcome start(StepContext context) {
    return new StepOutcome.RunLocal();
  }

  @Override
  public StepOutcome runLocal(StepContext context) {
    String id = context.conversation().publicId();
    ComicScript script = scenarios.script(id);
    List<BufferedImage> panels = panels(id, script);
    // 그림 모델이 정해 준 좌우를 어기면 말풍선이 다른 인물을 가리키므로, 그린 그림에서 자리와 얼굴 위치를 확인해 맞춘다
    List<Map<String, Staging.Side>> planned = Staging.panels(script);
    PanelPlacement.Spots spots;
    if (context.conversation().manualLlm()) {
      // 테스트 전용 수동 처리: 질문을 대기열 작업으로 두고, 답이 오면(onResult) 다시 여기로 와서 그 답으로 맞춘다
      Optional<byte[]> answered = storage.get(placementAnswer(id));
      if (answered.isPresent()) {
        List<Integer> asked = PanelPlacement.asked(script, planned, panels.size());
        spots =
            placement.answered(planned, asked, new String(answered.get(), StandardCharsets.UTF_8));
      } else {
        Optional<PanelPlacement.Question> question =
            placement.question(script, panels, planned, references(script));
        if (question.isPresent()) {
          return ask(id, question.get());
        }
        spots = PanelPlacement.fallback(planned);
      }
    } else {
      spots = placement.locate(script, panels, planned, references(script));
    }
    Map<String, String> portraits = portraits(id, script, panels, spots.heads());
    ComicComposer.Result result =
        composer.compose(script, panels, spots.stages(), spots.heads(), spots.props());
    String full = WorkKeys.comicFull(id);
    String thumb = WorkKeys.comicThumb(id);
    storage.put(full, result.full(), PNG);
    storage.put(thumb, result.thumb(), PNG);
    ObjectNode live = live(id, script, panels, spots);
    String language =
        context.conversation().language() == null ? "ko" : context.conversation().language();
    ContentClient.Registered registered =
        content.register(
            new ContentClient.RegisterComic(
                context.conversation().ownerId(),
                id,
                script.title(),
                full,
                thumb,
                language,
                script.nameWarnings() == null ? List.of() : script.nameWarnings(),
                cast(script, portraits),
                Concept.of(script.concept()).name(),
                Dialect.of(context.conversation().dialect()).name(),
                script.summary(),
                production(context.conversation().id()),
                live));
    return new StepOutcome.Completed(full, registered.comicId());
  }

  /**
   * 라이브 모드(docs specs/003-live-mode): 컷마다 말풍선을 넣은 그림과, 움직임 컷의 두 번째 그림(같은 말풍선 자리)을 만화 폴더에 저장하고, 컷별
   * 배경음을 골라 재생 정보(live.json)를 만든다. 두 번째 그림이 없거나 첫 그림과 어긋나면 그 컷은 한 장이다. 실패하면 null(만화 등록은 라이브 없이 한다).
   */
  ObjectNode live(
      String id, ComicScript script, List<BufferedImage> panels, PanelPlacement.Spots spots) {
    try {
      boolean gray = Concept.of(script.concept()) == Concept.NEWSPAPER;
      List<Boolean> moving = new ArrayList<>();
      for (int i = 0; i < panels.size(); i++) {
        Map<String, Staging.Side> stage = at(spots.stages(), i);
        Map<String, PanelPlacement.Head> heads = at(spots.heads(), i);
        List<PanelPlacement.Prop> props =
            spots.props() != null && i < spots.props().size() ? spots.props().get(i) : List.of();
        storage.put(
            WorkKeys.livePanel(id, i + 1, false),
            composer.livePanel(script, i, panels.get(i), stage, heads, props),
            PNG);
        boolean move = false;
        if (script.panels().get(i).liveMotion().isPresent()) {
          Optional<byte[]> second = storage.get(WorkKeys.panelB(id, i + 1));
          if (second.isPresent()) {
            BufferedImage b = read(second.get());
            b = gray ? gray(b) : b;
            move = LiveFrames.aligned(panels.get(i), b, heads);
            if (move) {
              storage.put(
                  WorkKeys.livePanel(id, i + 1, true),
                  composer.livePanel(script, i, b, stage, heads, props),
                  PNG);
            } else {
              log.info("두 그림이 어긋나 움직임을 뺀다: conversation={} panel={}", id, i + 1);
            }
          }
        }
        moving.add(move);
      }
      ObjectNode live = LivePlayback.build(json, script, sounds.match(queries(script)), moving);
      storage.put(
          WorkKeys.liveJson(id),
          json.writeValueAsString(live).getBytes(StandardCharsets.UTF_8),
          "application/json");
      return live;
    } catch (RuntimeException e) {
      log.warn("라이브 모드 정보를 만들지 못했다: conversation={} ({})", id, e.toString());
      return null;
    }
  }

  /** 컷별 배경음 질의: 소리 장면, 배경 문구의 첫 마디(장소), 대사. */
  static List<SoundMatcher.Query> queries(ComicScript script) {
    return script.panels().stream()
        .map(
            p ->
                new SoundMatcher.Query(
                    p.sound(),
                    p.setting() == null ? null : p.setting().split(",", 2)[0].strip(),
                    p.lines() == null
                        ? null
                        : String.join(
                            " ",
                            p.lines().stream()
                                .map(l -> l.text() == null ? "" : l.text())
                                .toList())))
        .toList();
  }

  private static <T> Map<String, T> at(List<Map<String, T>> list, int i) {
    return list != null && i < list.size() ? list.get(i) : Map.of();
  }

  /** 컷 그림들. 흑백 신문 만화는 흑백으로 바꾼다. 컷 그림이 없으면 IllegalStateException. */
  List<BufferedImage> panels(String id, ComicScript script) {
    boolean gray = Concept.of(script.concept()) == Concept.NEWSPAPER;
    List<BufferedImage> panels = new ArrayList<>();
    for (int i = 1; i <= script.panels().size(); i++) {
      String key = WorkKeys.panel(id, i);
      byte[] bytes = storage.get(key).orElseThrow(() -> new IllegalStateException(key + " 그림이 없다"));
      BufferedImage panel = read(bytes);
      panels.add(gray ? gray(panel) : panel);
    }
    return panels;
  }

  /**
   * 캐릭터를 못 찾은 인물의 얼굴 그림을 컷에서 잘라 저장한다. 실패해도 만화 등록은 한다(그 인물은 소개에 그림 없이 나온다).
   *
   * @return 화자 → 얼굴 그림 객체 키
   */
  Map<String, String> portraits(
      String id,
      ComicScript script,
      List<BufferedImage> panels,
      List<Map<String, PanelPlacement.Head>> heads) {
    Map<String, String> keys = new LinkedHashMap<>();
    try {
      Map<String, byte[]> cropped = CastPortraits.crop(script, panels, heads);
      List<ComicScript.Character> people = script.characters();
      for (int i = 0; i < people.size(); i++) {
        byte[] png = cropped.get(people.get(i).speaker());
        if (png != null) {
          String key = WorkKeys.castPortrait(id, i);
          storage.put(key, png, PNG);
          keys.put(people.get(i).speaker(), key);
        }
      }
    } catch (RuntimeException e) {
      log.warn("등장인물 얼굴 그림을 만들지 못했다: conversation={} ({})", id, e.toString());
    }
    return keys;
  }

  /** 지금까지의 제작 정보. 못 만들어도 만화 등록은 한다. */
  private JsonNode production(Long conversationId) {
    try {
      return production.of(conversationId, false).map(Production.Built::production).orElse(null);
    } catch (RuntimeException e) {
      log.warn("제작 정보를 만들지 못했다: conversation={}", conversationId, e);
      return null;
    }
  }

  /** 수동 처리 대화의 말풍선 자리 답을 둔 곳. */
  static String placementAnswer(String id) {
    return WorkKeys.work(id, "placement-answer.json");
  }

  /** 말풍선 자리 질문을 수동 LLM 작업으로 둔다. 그림은 저장소에 두고 키만 싣는다 (꺼낼 때 주소로 바꿔 준다). */
  private StepOutcome ask(String id, PanelPlacement.Question q) {
    ObjectNode payload = json.createObjectNode();
    payload.put("system", q.system().strip());
    payload.put("prompt", q.prompt());
    payload.set("jsonSchema", json.readTree(q.schema()));
    var keys = payload.putArray("imageKeys");
    for (int i = 0; i < q.images().size(); i++) {
      String key = WorkKeys.work(id, "placement/" + i + ".png");
      storage.put(key, q.images().get(i), PNG);
      keys.add(key);
    }
    return new StepOutcome.Enqueue(JobType.LLM, payload, null);
  }

  /** 수동 처리 대화의 말풍선 자리 답: 저장해 두고 다시 합성한다. */
  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    String id = context.conversation().publicId();
    storage.put(
        placementAnswer(id),
        result.path("content").asString("").getBytes(StandardCharsets.UTF_8),
        "application/json");
    return new StepOutcome.RunLocal();
  }

  /** 화자별 참조 그림(그림을 그릴 때 IP-Adapter에 쓴 것). 캐릭터를 못 찾은 인물은 없다. */
  Map<String, byte[]> references(ComicScript script) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    if (script.characters() == null) {
      return out;
    }
    for (ComicScript.Character c : script.characters()) {
      characters.referenceImage(c.lookId()).ifPresent(png -> out.put(c.speaker(), png));
    }
    return out;
  }

  /** 만화 아래 등장인물 소개: 호칭, 캐릭터 이름, 캐릭터 모습. */
  static List<ContentClient.CastMember> cast(ComicScript script) {
    return cast(script, Map.of());
  }

  /**
   * 만화 아래 등장인물 소개: 호칭, 캐릭터 이름, 캐릭터 모습, 캐릭터를 못 찾은 인물의 얼굴 그림.
   *
   * @param portraits 화자 → 얼굴 그림 객체 키
   */
  static List<ContentClient.CastMember> cast(ComicScript script, Map<String, String> portraits) {
    if (script.characters() == null) {
      return List.of();
    }
    return script.characters().stream()
        .map(
            c ->
                new ContentClient.CastMember(
                    c.callName(),
                    c.displayName(),
                    c.lookId(),
                    c.lookId() == null ? portraits.get(c.speaker()) : null))
        .toList();
  }

  /** 흑백 신문 만화는 옅은 색으로 그린 뒤 여기서 흑백으로 바꾼다. 그림 모델에 흑백을 바로 시키면 망점·잡티가 많았다(회차 2·3, 문구만 바꿔서는 줄지 않음). */
  static BufferedImage gray(BufferedImage image) {
    BufferedImage g =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D d = g.createGraphics();
    try {
      d.drawImage(image, 0, 0, null);
    } finally {
      d.dispose();
    }
    BufferedImage out =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D o = out.createGraphics();
    try {
      o.drawImage(g, 0, 0, null);
    } finally {
      o.dispose();
    }
    return out;
  }

  private static BufferedImage read(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new IllegalStateException("PNG를 읽을 수 없다");
      }
      return image;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
