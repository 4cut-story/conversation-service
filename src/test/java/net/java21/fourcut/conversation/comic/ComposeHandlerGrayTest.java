package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComposeHandlerGrayTest {

  @Test
  @DisplayName("FR-COMIC: 흑백 신문 만화는 색으로 그린 컷을 합성 전에 흑백으로 바꾼다")
  void gray() {
    BufferedImage color = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
    color.setRGB(0, 0, 0xCC3311);
    color.setRGB(3, 1, 0x2266EE);

    BufferedImage out = ComposeHandler.gray(color);

    assertThat(out.getWidth()).isEqualTo(4);
    assertThat(out.getHeight()).isEqualTo(2);
    for (int[] p : new int[][] {{0, 0}, {3, 1}, {1, 1}}) {
      int rgb = out.getRGB(p[0], p[1]);
      int r = (rgb >> 16) & 0xFF;
      int g = (rgb >> 8) & 0xFF;
      int b = rgb & 0xFF;
      assertThat(r).isEqualTo(g).isEqualTo(b);
    }
    assertThat(out.getRGB(0, 0) & 0xFF).isBetween(1, 254);
  }
}
