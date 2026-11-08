package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CastPortraitsTest {

  private static ComicScript script() {
    return new ComicScript(
        "t",
        List.of(
            new ComicScript.Character("A", "해담", "", "little boy swordsman", "소협", null),
            new ComicScript.Character("B", "송보원", "", "old man", "노고수", "look1"),
            new ComicScript.Character("C", "하늘", "", "girl", "단짝", null)),
        List.of(),
        true,
        List.of());
  }

  /** 왼쪽 위 사분면만 빨간 컷. */
  private static BufferedImage panel() {
    BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 400, 400);
    g.setColor(Color.RED);
    g.fillRect(0, 0, 200, 200);
    g.dispose();
    return image;
  }

  @Test
  @DisplayName("REQ-018: 캐릭터를 못 찾은 인물만 얼굴이 가장 크게 보인 컷에서 자르고, 얼굴을 모르거나 너무 작으면 뺀다")
  void crop() throws Exception {
    List<Map<String, PanelPlacement.Head>> heads =
        List.of(
            Map.of(
                "A", new PanelPlacement.Head(0.7, 0.7, 0.1),
                "B", new PanelPlacement.Head(0.5, 0.5, 0.3),
                "C", new PanelPlacement.Head(0.5, 0.5, 0.03)),
            Map.of("A", new PanelPlacement.Head(0.2, 0.2, 0.2)));
    Map<String, byte[]> out = CastPortraits.crop(script(), List.of(panel(), panel()), heads);

    assertThat(out).containsOnlyKeys("A");
    BufferedImage a = ImageIO.read(new ByteArrayInputStream(out.get("A")));
    assertThat(a.getWidth()).isEqualTo(CastPortraits.SIZE);
    assertThat(a.getHeight()).isEqualTo(CastPortraits.SIZE);
    // 두 번째 컷(얼굴이 큰 쪽)의 왼쪽 위에서 잘라 빨간색이 보인다
    assertThat(new Color(a.getRGB(10, 10))).isEqualTo(Color.RED);
  }

  @Test
  @DisplayName("REQ-018: 자르는 칸이 그림 밖으로 나가면 안쪽으로 밀고, 얼굴이 아주 커도 그림보다 크게 자르지 않는다")
  void square() {
    BufferedImage edge = CastPortraits.square(panel(), new PanelPlacement.Head(0.98, 0.98, 0.1));
    assertThat(new Color(edge.getRGB(CastPortraits.SIZE - 1, CastPortraits.SIZE - 1)))
        .isEqualTo(Color.WHITE);
    BufferedImage big = CastPortraits.square(panel(), new PanelPlacement.Head(0.5, 0.5, 0.8));
    // 그림 전체를 줄인 것: 왼쪽 위 빨강, 오른쪽 아래 하양
    assertThat(new Color(big.getRGB(5, 5))).isEqualTo(Color.RED);
    assertThat(new Color(big.getRGB(250, 250))).isEqualTo(Color.WHITE);
  }

  @Test
  @DisplayName("REQ-018: 인물이 없는 콘티는 빈 결과")
  void noCharacters() {
    ComicScript empty = new ComicScript("t", null, List.of(), true, List.of());
    assertThat(CastPortraits.crop(empty, List.of(), List.of())).isEmpty();
  }
}
