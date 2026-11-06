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
            "wide",
            "장면",
            List.of(
                new ComicScript.Line("A", "내 접시 누가 다 먹었어? 정말 궁금해서 그래, 말해 줘"),
                new ComicScript.Line("B", "나 아니야")));
    ComicScript script =
        new ComicScript(
            "접시 미스터리",
            List.of(
                new ComicScript.Character("A", "민지", "먹보", "girl, bob hair"),
                new ComicScript.Character("B", "도윤", "능청꾼", "boy, glasses")),
            List.of(panel, panel, panel, new ComicScript.Panel("결", "close-up", "끝", null)),
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

  @Test
  @DisplayName("FR-007: 대사 앞에 붙은 화자 표시는 지우고 가명을 한 번만 쓴다")
  void bubbleText() {
    assertThat(ComicComposer.bubbleText("민지", new ComicScript.Line("A", "A: A: 내 접시!")))
        .isEqualTo("민지: 내 접시!");
    assertThat(ComicComposer.bubbleText("민지", new ComicScript.Line("A", "민지： 어디 갔어")))
        .isEqualTo("민지: 어디 갔어");
    assertThat(ComicComposer.bubbleText("민지", new ComicScript.Line("A", "Apple 맛있다")))
        .isEqualTo("민지: Apple 맛있다");
  }

  @Test
  @DisplayName("FR-007: 긴 제목은 글자를 줄이고 그래도 넘치면 말줄임표로 잘라 폭 안에 그린다")
  void longTitle() throws Exception {
    ComicScript.Panel panel = new ComicScript.Panel("기", "wide", "장면", List.of());
    String title = "아주아주 길고 길어서 한 줄에 절대로 다 들어가지 않을 것 같은 만화 제목입니다 정말로";
    ComicScript script =
        new ComicScript(
            title,
            List.of(new ComicScript.Character("A", "민지", "", "girl")),
            List.of(panel, panel, panel, panel),
            true,
            List.of());
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    composer.compose(script, List.of(image, image, image, image));
    Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
    g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
    String cut = ComicComposer.ellipsize(title, g.getFontMetrics(), 300);
    assertThat(cut).endsWith("…");
    assertThat(g.getFontMetrics().stringWidth(cut)).isLessThanOrEqualTo(300);
    g.dispose();
  }

  @Test
  @DisplayName("FR-007: 말풍선은 화자 쪽에 붙고, 꼬리는 화자 쪽으로 나온다")
  void bubbleSide() {
    int w = 200;
    int left = ComicComposer.bubbleX(Staging.Side.LEFT, false, w);
    int right = ComicComposer.bubbleX(Staging.Side.RIGHT, true, w);
    int center = ComicComposer.bubbleX(Staging.Side.CENTER, true, w);
    assertThat(left).isLessThan(center);
    assertThat(center).isLessThan(right);
    // 화자를 모르면 위는 왼쪽, 아래는 오른쪽
    assertThat(ComicComposer.bubbleX(null, true, w)).isEqualTo(left);
    assertThat(ComicComposer.bubbleX(null, false, w)).isEqualTo(right);

    // 오른쪽 화자의 위 말풍선: 꼬리 끝은 말풍선 아래, 화자 쪽(오른쪽)이다
    var rect = new java.awt.Rectangle(right, 40, w, 100);
    var tail = ComicComposer.tail(Staging.Side.RIGHT, true, right, 40, w, 100).getBounds();
    assertThat(tail.y + tail.height).isGreaterThan(rect.y + rect.height + 40);
    assertThat(tail.x + tail.width / 2).isGreaterThan(ComicComposer.speakerX(Staging.Side.CENTER));
    // 왼쪽 화자의 아래 말풍선: 꼬리는 위로, 왼쪽에 있다
    var low = ComicComposer.tail(Staging.Side.LEFT, false, left, 600, w, 100).getBounds();
    assertThat(low.y).isLessThan(600 - 40);
    assertThat(low.x + low.width).isLessThan(ComicComposer.speakerX(Staging.Side.CENTER));
    assertThat(ComicComposer.speakerX(Staging.Side.LEFT))
        .isLessThan(ComicComposer.speakerX(Staging.Side.CENTER));
  }
}
