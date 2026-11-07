package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.character.CharacterLibrary;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Concept;
import net.java21.fourcut.conversation.scenario.Dialect;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;

/** COMPOSE: 컷(4~10장)에 말풍선을 그려 세로 만화 한 장과 썸네일을 만들고, content-service 만화함에 등록한다. 서버에서 실행한다. */
@Component
class ComposeHandler implements StepHandler {

  private static final String PNG = "image/png";

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComicComposer composer;
  private final ContentClient content;
  private final PanelPlacement placement;
  private final CharacterLibrary characters;

  ComposeHandler(
      ScenarioStore scenarios,
      ObjectStorage storage,
      ComicComposer composer,
      ContentClient content,
      PanelPlacement placement,
      CharacterLibrary characters) {
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
    boolean gray = Concept.of(script.concept()) == Concept.NEWSPAPER;
    List<BufferedImage> panels = new ArrayList<>();
    for (int i = 1; i <= script.panels().size(); i++) {
      String key = WorkKeys.panel(id, i);
      byte[] bytes = storage.get(key).orElseThrow(() -> new IllegalStateException(key + " 그림이 없다"));
      BufferedImage panel = read(bytes);
      panels.add(gray ? gray(panel) : panel);
    }
    // 그림 모델이 정해 준 좌우를 어기면 말풍선이 다른 인물을 가리키므로, 그린 그림에서 자리와 얼굴 위치를 확인해 맞춘다
    PanelPlacement.Spots spots =
        placement.locate(script, panels, Staging.panels(script), references(script));
    ComicComposer.Result result = composer.compose(script, panels, spots.stages(), spots.heads());
    String full = WorkKeys.comicFull(id);
    String thumb = WorkKeys.comicThumb(id);
    storage.put(full, result.full(), PNG);
    storage.put(thumb, result.thumb(), PNG);
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
                cast(script),
                Concept.of(script.concept()).name(),
                Dialect.of(context.conversation().dialect()).name()));
    return new StepOutcome.Completed(full, registered.comicId());
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
    if (script.characters() == null) {
      return List.of();
    }
    return script.characters().stream()
        .map(c -> new ContentClient.CastMember(c.callName(), c.displayName(), c.lookId()))
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
