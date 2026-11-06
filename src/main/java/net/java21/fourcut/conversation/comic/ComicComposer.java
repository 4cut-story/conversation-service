package net.java21.fourcut.conversation.comic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.springframework.stereotype.Component;

/**
 * 세로 4컷 합성. 그림 위에 말풍선을 코드로 그려 한글이 깨지지 않게 한다 (FR-007). 글꼴은 Noto Sans CJK(이미지에 설치)를 먼저 찾는다.
 *
 * <p>말풍선은 화자가 선 쪽({@link Staging}: 그림을 그릴 때와 같은 자리)에 붙이고, 꼬리가 화자를 가리킨다. 첫 대사는 위, 둘째 대사는 아래에 둔다. 화자가
 * 그 컷에 없으면(자리를 모르면) 꼬리 없이 예전처럼 왼쪽 위·오른쪽 아래에 둔다.
 */
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
  private static final int TITLE_FONT = 40;
  private static final int TITLE_FONT_MIN = 24;
  private static final int BUBBLE_MAX = (int) (PANEL * 0.72);

  /** 화자가 왼쪽·오른쪽에 서면 말풍선을 좁혀 어느 쪽인지 분명히 한다. */
  private static final int BUBBLE_MAX_SIDE = (int) (PANEL * 0.62);

  private static final int TAIL = 64;
  private static final int TAIL_BASE = 26;
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
      String title = fitTitle(g, script.title());
      FontMetrics fm = g.getFontMetrics();
      g.drawString(
          title,
          (WIDTH - fm.stringWidth(title)) / 2,
          MARGIN + TITLE_HEIGHT / 2 + fm.getAscent() / 2 - 6);
      int y = MARGIN + TITLE_HEIGHT;
      Map<String, String> names = script.callNames();
      List<Map<String, Staging.Side>> stages = Staging.panels(script);
      for (int i = 0; i < panels.size(); i++) {
        g.drawImage(panels.get(i), MARGIN, y, PANEL, PANEL, null);
        g.setStroke(new BasicStroke(4f));
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, y, PANEL, PANEL);
        List<ComicScript.Line> lines = script.panels().get(i).lines();
        Map<String, Staging.Side> stage = i < stages.size() ? stages.get(i) : Map.of();
        drawBubbles(g, lines == null ? List.of() : lines, names, stage, y);
        y += PANEL + GAP;
      }
    } finally {
      g.dispose();
    }
    return new Result(png(out), png(scale(out, THUMB_WIDTH)));
  }

  private void drawBubbles(
      Graphics2D g,
      List<ComicScript.Line> lines,
      Map<String, String> names,
      Map<String, Staging.Side> stage,
      int panelTop) {
    g.setFont(new Font(family, Font.PLAIN, 28));
    FontMetrics fm = g.getFontMetrics();
    for (int i = 0; i < lines.size(); i++) {
      ComicScript.Line line = lines.get(i);
      Staging.Side side = line.speaker() == null ? null : stage.get(line.speaker());
      boolean top = i % 2 == 0;
      int max =
          side == Staging.Side.LEFT || side == Staging.Side.RIGHT ? BUBBLE_MAX_SIDE : BUBBLE_MAX;
      List<String> rows =
          wrap(
              bubbleText(names.getOrDefault(line.speaker(), line.speaker()), line),
              fm,
              max - 2 * BUBBLE_PAD);
      int w = rows.stream().mapToInt(fm::stringWidth).max().orElse(0) + 2 * BUBBLE_PAD;
      int h = rows.size() * fm.getHeight() + 2 * BUBBLE_PAD;
      int x = bubbleX(side, top, w);
      int y = top ? panelTop + 16 : panelTop + PANEL - 16 - h;
      Area shape = new Area(new RoundRectangle2D.Float(x, y, w, h, 28, 28));
      if (side != null) {
        shape.add(new Area(tail(side, top, x, y, w, h)));
      }
      g.setColor(new Color(255, 255, 255, 235));
      g.fill(shape);
      g.setColor(Color.BLACK);
      g.setStroke(new BasicStroke(3f));
      g.draw(shape);
      int baseline = y + BUBBLE_PAD + fm.getAscent();
      for (String row : rows) {
        g.drawString(row, x + BUBBLE_PAD, baseline);
        baseline += fm.getHeight();
      }
    }
  }

  /** 말풍선 왼쪽 x. 왼쪽 화자는 왼쪽, 오른쪽 화자는 오른쪽, 가운데 화자는 가운데, 모르면 위는 왼쪽·아래는 오른쪽. */
  static int bubbleX(Staging.Side side, boolean top, int w) {
    int left = MARGIN + 16;
    int right = MARGIN + PANEL - 16 - w;
    if (side == Staging.Side.LEFT) {
      return left;
    }
    if (side == Staging.Side.RIGHT) {
      return right;
    }
    if (side == Staging.Side.CENTER) {
      return MARGIN + (PANEL - w) / 2;
    }
    return top ? left : right;
  }

  /** 화자 쪽 그림 위치의 x (컷 안 왼쪽 30%, 오른쪽 70%, 가운데 50%). */
  static int speakerX(Staging.Side side) {
    return switch (side) {
      case LEFT -> MARGIN + PANEL * 30 / 100;
      case RIGHT -> MARGIN + PANEL * 70 / 100;
      case CENTER -> MARGIN + PANEL / 2;
    };
  }

  /**
   * 말풍선 꼬리: 위 말풍선은 아래로, 아래 말풍선은 위로 나와 화자 쪽(머리 높이쯤인 컷 높이 45%)을 향한다. 밑변은 말풍선 가장자리 안, 화자 쪽에 가장 가까운
   * 곳이다.
   */
  static Path2D tail(Staging.Side side, boolean top, int x, int y, int w, int h) {
    int target = speakerX(side);
    int lo = x + 28;
    int hi = x + w - 28 - TAIL_BASE;
    int baseX = Math.max(lo, Math.min(hi, target - TAIL_BASE / 2));
    int edge = top ? y + h - 2 : y + 2;
    int tipY = top ? edge + TAIL : edge - TAIL;
    // 끝은 화자 쪽으로 기울이되 너무 눕지 않게 꼬리 길이만큼만 옆으로 간다
    int mid = baseX + TAIL_BASE / 2;
    int tipX = mid + Math.max(-TAIL, Math.min(TAIL, target - mid));
    Path2D tail = new Path2D.Float();
    tail.moveTo(baseX, edge);
    tail.lineTo(tipX, tipY);
    tail.lineTo(baseX + TAIL_BASE, edge);
    tail.closePath();
    return tail;
  }

  /** 제목이 폭(여백 제외)에 들어가게 글자 크기를 줄이고, 가장 작은 크기로도 넘치면 끝을 말줄임표로 자른다. g에 맞춘 글꼴을 남긴다. */
  private String fitTitle(Graphics2D g, String title) {
    int max = WIDTH - 2 * MARGIN;
    for (int size = TITLE_FONT; size >= TITLE_FONT_MIN; size -= 2) {
      g.setFont(new Font(family, Font.BOLD, size));
      if (g.getFontMetrics().stringWidth(title) <= max) {
        return title;
      }
    }
    return ellipsize(title, g.getFontMetrics(), max);
  }

  static String ellipsize(String text, FontMetrics fm, int maxWidth) {
    String cut = text;
    while (!cut.isEmpty() && fm.stringWidth(cut + "…") > maxWidth) {
      cut = cut.substring(0, cut.offsetByCodePoints(cut.length(), -1));
    }
    return cut.strip() + "…";
  }

  /** 말풍선 글: "호칭: 대사"(호칭이 없는 예전 콘티는 "이름: 대사"). LLM이 대사 앞에 화자("A:", "민지:")를 붙여 내도 한 번만 보이게 지운다. */
  static String bubbleText(String name, ComicScript.Line line) {
    String text = line.text().strip();
    boolean stripped = true;
    while (stripped) {
      stripped = false;
      for (String who : new String[] {line.speaker(), name}) {
        if (who == null || who.isBlank()) {
          continue;
        }
        for (String sep : new String[] {":", "："}) {
          if (text.startsWith(who + sep)) {
            text = text.substring(who.length() + sep.length()).strip();
            stripped = true;
          }
        }
      }
    }
    return name + ": " + text;
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
