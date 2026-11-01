package net.java21.fourcut.conversation.comic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;

/** COMPOSE: 컷 4장에 말풍선을 그려 세로 4컷 한 장과 썸네일을 만들고, content-service 만화함에 등록한다. 서버에서 실행한다. */
@Component
class ComposeHandler implements StepHandler {

  private static final String PNG = "image/png";

  private final ScenarioStore scenarios;
  private final ObjectStorage storage;
  private final ComicComposer composer;
  private final ContentClient content;

  ComposeHandler(
      ScenarioStore scenarios,
      ObjectStorage storage,
      ComicComposer composer,
      ContentClient content) {
    this.scenarios = scenarios;
    this.storage = storage;
    this.composer = composer;
    this.content = content;
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
    List<BufferedImage> panels = new ArrayList<>();
    for (int i = 1; i <= DrawHandler.PANELS; i++) {
      String key = WorkKeys.panel(id, i);
      byte[] bytes = storage.get(key).orElseThrow(() -> new IllegalStateException(key + " 그림이 없다"));
      panels.add(read(bytes));
    }
    ComicComposer.Result result = composer.compose(script, panels);
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
                script.nameWarnings() == null ? List.of() : script.nameWarnings()));
    return new StepOutcome.Completed(full, registered.comicId());
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
