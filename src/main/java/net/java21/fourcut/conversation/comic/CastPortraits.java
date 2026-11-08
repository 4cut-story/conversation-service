package net.java21.fourcut.conversation.comic;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;

/**
 * 캐릭터 DB에서 찾지 못한 인물(무협·조선의 아이, 맞는 캐릭터가 없는 인물)의 얼굴 그림을 만화 컷에서 잘라 낸다. 이런 인물은 캐릭터 모습 그림이 없어 등장인물 소개가
 * 회색 빈칸이었다. 말풍선 자리를 맞출 때 받은 얼굴 위치({@link PanelPlacement.Head})에서 얼굴이 가장 크게 보인 컷을 골라 얼굴과 어깨가 들어가는
 * 정사각형으로 자른다. 만화에 실제로 그려진 모습이라 소개와 만화가 어긋나지 않고, 그림 모델을 다시 부르지 않는다.
 */
final class CastPortraits {

  /** 저장하는 얼굴 그림 한 변(픽셀). 등장인물 소개 칸이 작아 이 정도면 된다. */
  static final int SIZE = 256;

  /** 자르는 한 변 = 얼굴 높이 × 이 배수. 얼굴만 자르면 너무 꽉 차서 머리·어깨까지 넣는다. */
  static final double SPAN = 2.4;

  /** 얼굴을 위쪽에 두도록 자르는 칸을 얼굴 높이 × 이만큼 아래로 내린다. */
  static final double DROP = 0.25;

  /** 이보다 작은 얼굴(컷 높이 대비)은 잘라도 알아보기 어려워 쓰지 않는다. */
  static final double MIN_FACE = 0.06;

  private CastPortraits() {}

  /**
   * 화자 → 얼굴 그림 PNG. 캐릭터 모습이 있는 인물, 얼굴 위치를 모르는 인물은 빠진다.
   *
   * @param panels 컷 그림 (합성에 쓴 것과 같은 순서)
   * @param heads 컷별 인물 얼굴 ({@link PanelPlacement.Spots#heads()})
   */
  static Map<String, byte[]> crop(
      ComicScript script,
      List<BufferedImage> panels,
      List<Map<String, PanelPlacement.Head>> heads) {
    Map<String, byte[]> out = new LinkedHashMap<>();
    if (script.characters() == null) {
      return out;
    }
    for (ComicScript.Character c : script.characters()) {
      if (c.lookId() != null) {
        continue;
      }
      int best = -1;
      PanelPlacement.Head face = null;
      for (int i = 0; i < Math.min(panels.size(), heads.size()); i++) {
        PanelPlacement.Head h = heads.get(i).get(c.speaker());
        if (h != null && h.size() >= MIN_FACE && (face == null || h.size() > face.size())) {
          best = i;
          face = h;
        }
      }
      if (face != null) {
        out.put(c.speaker(), png(square(panels.get(best), face)));
      }
    }
    return out;
  }

  /** 얼굴을 위쪽 가운데에 둔 정사각형. 그림 밖으로 나가면 안쪽으로 민다. */
  static BufferedImage square(BufferedImage panel, PanelPlacement.Head face) {
    int w = panel.getWidth();
    int h = panel.getHeight();
    double faceH = face.size() * h;
    int side = (int) Math.round(Math.min(Math.min(w, h), Math.max(faceH * SPAN, 32)));
    int cx = (int) Math.round(face.x() * w);
    int cy = (int) Math.round(face.y() * h + faceH * DROP);
    int x = clamp(cx - side / 2, 0, w - side);
    int y = clamp(cy - side / 2, 0, h - side);
    BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(panel, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(v, max));
  }

  private static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
