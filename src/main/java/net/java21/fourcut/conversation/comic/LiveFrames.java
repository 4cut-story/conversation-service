package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * 라이브 모드 두 그림이 어긋났는지 본다 (docs specs/003-live-mode R4, FR-015). 두 번째 그림은 배경만 바꿔야 하므로, 전체에서 크게 바뀐 픽셀이
 * 너무 많거나 첫 그림에서 찾은 얼굴 자리 안이 많이 바뀌었으면 어긋난 것으로 보고 움직임을 뺀다.
 */
final class LiveFrames {

  /** 비교할 때 줄이는 한 변. 작은 차이(선 떨림)는 줄이며 사라진다. */
  static final int SAMPLE = 128;

  /** 크게 바뀌었다고 보는 픽셀 차이(RGB 평균, 0~255). */
  static final int CHANGED = 48;

  /** 크게 바뀐 픽셀 비율 상한. 넘으면 그림 전체가 다시 그려진 것이다. */
  static final double MAX_CHANGED = 0.35;

  /** 얼굴 자리 평균 차이 상한(0~255). 넘으면 얼굴이 바뀐 것이다. */
  static final double MAX_FACE_DIFF = 24;

  private LiveFrames() {}

  /** 두 그림이 같은 구도·같은 인물인지. heads는 첫 그림에서 찾은 얼굴(컷 좌표 0~1). */
  static boolean aligned(BufferedImage a, BufferedImage b, Map<String, PanelPlacement.Head> heads) {
    int[][] x = sample(a);
    int[][] y = sample(b);
    long changed = 0;
    for (int r = 0; r < SAMPLE; r++) {
      for (int c = 0; c < SAMPLE; c++) {
        if (diff(x[r][c], y[r][c]) > CHANGED) {
          changed++;
        }
      }
    }
    if ((double) changed / (SAMPLE * SAMPLE) > MAX_CHANGED) {
      return false;
    }
    for (PanelPlacement.Head h :
        heads == null ? java.util.List.<PanelPlacement.Head>of() : heads.values()) {
      if (faceDiff(x, y, h) > MAX_FACE_DIFF) {
        return false;
      }
    }
    return true;
  }

  /** 얼굴 자리(머리 크기 반지름) 안의 평균 차이. */
  static double faceDiff(int[][] x, int[][] y, PanelPlacement.Head h) {
    int cx = (int) Math.round(h.x() * SAMPLE);
    int cy = (int) Math.round(h.y() * SAMPLE);
    int r = Math.max(2, (int) Math.round(h.size() * SAMPLE * 0.5));
    long sum = 0;
    int n = 0;
    for (int row = Math.max(0, cy - r); row < Math.min(SAMPLE, cy + r); row++) {
      for (int col = Math.max(0, cx - r); col < Math.min(SAMPLE, cx + r); col++) {
        sum += diff(x[row][col], y[row][col]);
        n++;
      }
    }
    return n == 0 ? 0 : (double) sum / n;
  }

  private static int diff(int p, int q) {
    int dr = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF));
    int dg = Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF));
    int db = Math.abs((p & 0xFF) - (q & 0xFF));
    return (dr + dg + db) / 3;
  }

  private static int[][] sample(BufferedImage image) {
    BufferedImage small = new BufferedImage(SAMPLE, SAMPLE, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = small.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.drawImage(image, 0, 0, SAMPLE, SAMPLE, null);
    } finally {
      g.dispose();
    }
    int[][] out = new int[SAMPLE][SAMPLE];
    for (int r = 0; r < SAMPLE; r++) {
      for (int c = 0; c < SAMPLE; c++) {
        out[r][c] = small.getRGB(c, r);
      }
    }
    return out;
  }
}
