package net.java21.fourcut.conversation.comic;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

/** 로컬 도구: COMPOSE_JOBS 폴더마다 script.json과 panel-N.png로 말풍선 만화를 합성해 full.png를 쓴다. 커밋하지 않는다. */
class ComposeJobsHarness {

  @Test
  @EnabledIfEnvironmentVariable(named = "COMPOSE_JOBS", matches = ".+")
  void run() throws Exception {
    ComicComposer composer = new ComicComposer();
    JsonMapper json = JsonMapper.builder().build();
    File[] jobs = new File(System.getenv("COMPOSE_JOBS")).listFiles(File::isDirectory);
    for (File job : jobs) {
      ComicScript script =
          json.readValue(new File(job, "script.json"), ComicScript.class).withRoleCalls();
      List<BufferedImage> panels = new ArrayList<>();
      for (int i = 1; new File(job, "panel-" + i + ".png").exists(); i++) {
        panels.add(ImageIO.read(new File(job, "panel-" + i + ".png")));
      }
      ComicComposer.Result r = composer.compose(script, panels);
      Files.write(new File(job, "full.png").toPath(), r.full());
      System.out.println("composed " + job.getName());
    }
  }
}
