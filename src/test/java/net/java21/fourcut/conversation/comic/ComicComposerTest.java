package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComicComposerTest {

  private final ComicComposer composer = new ComicComposer();

  @Test
  @DisplayName("FR-007: 컷 4장과 말풍선을 세로 한 장으로 합치고 썸네일을 만든다")
  void compose() throws Exception {
    ComicScript.Panel panel =
        new ComicScript.Panel(
            "기",
            "장면",
            List.of(
                new ComicScript.Line("A", "내 접시 누가 다 먹었어? 정말 궁금해서 그래, 말해 줘"),
                new ComicScript.Line("B", "나 아니야")));
    ComicScript script =
        new ComicScript(
            "접시 미스터리",
            List.of(new ComicScript.Character("A", "단발"), new ComicScript.Character("B", "안경")),
            List.of(panel, panel, panel, new ComicScript.Panel("결", "끝", null)),
            true,
            List.of());
    BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
    ComicComposer.Result result = composer.compose(script, List.of(image, image, image, image));

    BufferedImage full = ImageIO.read(new ByteArrayInputStream(result.full()));
    BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(result.thumb()));
    assertThat(full.getWidth()).isEqualTo(ComicComposer.WIDTH);
    assertThat(full.getHeight()).isGreaterThan(full.getWidth() * 3);
    assertThat(thumb.getWidth()).isEqualTo(ComicComposer.THUMB_WIDTH);
    assertThat(composer.family()).isNotBlank();
  }

  @Test
  @DisplayName("말풍선 글은 폭에 맞춰 글자 단위로 줄을 나눈다")
  void wrap() {
    Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
    g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
    FontMetrics fm = g.getFontMetrics();
    List<String> rows = ComicComposer.wrap("가나다라마바사아자차카타파하", fm, fm.stringWidth("가나다라마"));
    assertThat(rows).hasSizeGreaterThan(2);
    assertThat(String.join("", rows)).isEqualTo("가나다라마바사아자차카타파하");
    g.dispose();
  }
}
