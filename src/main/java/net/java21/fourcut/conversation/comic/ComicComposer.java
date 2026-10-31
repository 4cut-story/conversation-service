package net.java21.fourcut.conversation.comic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.springframework.stereotype.Component;

/** 세로 4컷 합성. 그림 위에 말풍선을 코드로 그려 한글이 깨지지 않게 한다 (FR-007). 글꼴은 Noto Sans CJK(이미지에 설치)를 먼저 찾는다. */
@Component
public class ComicComposer {

  /** 완성 이미지 폭. */
  public static final int WIDTH = 800;

  /** 썸네일 폭. */
  public static final int THUMB_WIDTH = 400;

  private static final int MARGIN = 24;
  private static final int GAP = 20;
  private static final int TITLE_HEIGHT = 90;
  private static final int PANEL = WIDTH - 2 * MARGIN;
  private static final int BUBBLE_PAD = 14;
  private static final int BUBBLE_MAX = (int) (PANEL * 0.72);
  private static final Set<String> PREFERRED =
      Set.of(
          "Noto Sans CJK KR",
          "Noto Sans KR",
          "NanumGothic",
          "Malgun Gothic",
          "Apple SD Gothic Neo");

  private final String family;

  public ComicComposer() {
    this.family = pickFamily();
  }

  /** 합성 결과 PNG. */
  public record Result(byte[] full, byte[] thumb) {}

  public Result compose(ComicScript script, List<BufferedImage> panels) {
    int height = MARGIN + TITLE_HEIGHT + panels.size() * (PANEL + GAP) - GAP + MARGIN;
    BufferedImage out = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      quality(g);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, WIDTH, height);
      g.setColor(Color.BLACK);
      g.setFont(new Font(family, Font.BOLD, 40));
      FontMetrics fm = g.getFontMetrics();
      String title = script.title();
      g.drawString(
          title,
          (WIDTH - fm.stringWidth(title)) / 2,
          MARGIN + TITLE_HEIGHT / 2 + fm.getAscent() / 2 - 6);
      int y = MARGIN + TITLE_HEIGHT;
      for (int i = 0; i < panels.size(); i++) {
        g.drawImage(panels.get(i), MARGIN, y, PANEL, PANEL, null);
        g.setStroke(new BasicStroke(4f));
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, y, PANEL, PANEL);
        List<ComicScript.Line> lines = script.panels().get(i).lines();
        drawBubbles(g, lines == null ? List.of() : lines, y);
        y += PANEL + GAP;
      }
    } finally {
      g.dispose();
    }
    return new Result(png(out), png(scale(out, THUMB_WIDTH)));
  }

  private void drawBubbles(Graphics2D g, List<ComicScript.Line> lines, int panelTop) {
    g.setFont(new Font(family, Font.PLAIN, 28));
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < lines.size(); i++) {
      ComicScript.Line line = lines.get(i);
      List<String> rows =
          wrap(line.speaker() + ": " + line.text(), fm, BUBBLE_MAX - 2 * BUBBLE_PAD);
      int w = rows.stream().mapToInt(fm::stringWidth).max().orElse(0) + 2 * BUBBLE_PAD;
      int h = rows.size() * fm.getHeight() + 2 * BUBBLE_PAD;
      boolean top = i % 2 == 0;
      int x = top ? MARGIN + 16 : MARGIN + PANEL - 16 - w;
      int y = top ? panelTop + 16 : panelTop + PANEL - 16 - h;
      RoundRectangle2D bubble = new RoundRectangle2D.Float(x, y, w, h, 28, 28);
      g.setColor(new Color(255, 255, 255, 235));
      g.fill(bubble);
      g.setColor(Color.BLACK);
      g.setStroke(new BasicStroke(3f));
      g.draw(bubble);
      int baseline = y + BUBBLE_PAD + fm.getAscent();
      for (String row : rows) {
        g.drawString(row, x + BUBBLE_PAD, baseline);
        baseline += fm.getHeight();
      }
    }
  }

  /** 폭에 맞춰 글자 단위로 줄을 나눈다 (한글은 띄어쓰기가 없어도 나눌 수 있게). */
  static List<String> wrap(String text, FontMetrics fm, int maxWidth) {
    List<String> rows = new ArrayList<>();
    StringBuilder row = new StringBuilder();
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      String ch = new String(Character.toChars(cp));
      if (row.length() > 0 && fm.stringWidth(row + ch) > maxWidth) {
        rows.add(row.toString().strip());
        row.setLength(0);
      }
      row.append(ch);
      i += Character.charCount(cp);
    }
    if (!row.toString().isBlank()) {
      rows.add(row.toString().strip());
    }
    return rows;
  }

  String family() {
    return family;
  }

  private static String pickFamily() {
    String[] installed =
        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
    for (String name : installed) {
      if (PREFERRED.contains(name)) {
        return name;
      }
    }
    for (String name : installed) {
      if (new Font(name, Font.PLAIN, 12).canDisplayUpTo("가나다") == -1) {
        return name;
      }
    }
    return Font.SANS_SERIF;
  }

  private static BufferedImage scale(BufferedImage source, int width) {
    int height = source.getHeight() * width / source.getWidth();
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      quality(g);
      g.drawImage(source, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static void quality(Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
  }

  private static byte[] png(BufferedImage image) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
