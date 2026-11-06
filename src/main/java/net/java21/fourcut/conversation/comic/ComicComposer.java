package net.java21.fourcut.conversation.comic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
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
 * <p>그림에서 화자 얼굴 위치를 알면({@link PanelPlacement.Head}) 말풍선을 그 얼굴 위(자리가 없으면 아래·옆)에 얼굴과 다른 말풍선을 가리지 않게
 * 놓고, 꼬리가 얼굴을 가리킨다. 얼굴을 모르면 화자가 선 쪽({@link Staging})에 붙이고 첫 대사는 위, 둘째 대사는 아래에 둔다. 화자가 그 컷에 없으면 꼬리
 * 없이 왼쪽 위·오른쪽 아래에 둔다.
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
    return compose(script, panels, Staging.panels(script));
  }

  /** stages: 컷별 인물 자리(그림에서 확인해 고친 자리). 말풍선을 그 쪽에 둔다. */
  Result compose(
      ComicScript script, List<BufferedImage> panels, List<Map<String, Staging.Side>> stages) {
    return compose(script, panels, stages, List.of());
  }

  /** heads: 컷별 인물 얼굴(그림에서 본 위치). 아는 화자는 말풍선을 얼굴 쪽에 둔다. */
  Result compose(
      ComicScript script,
      List<BufferedImage> panels,
      List<Map<String, Staging.Side>> stages,
      List<Map<String, PanelPlacement.Head>> heads) {
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
      for (int i = 0; i < panels.size(); i++) {
        g.drawImage(panels.get(i), MARGIN, y, PANEL, PANEL, null);
        g.setStroke(new BasicStroke(4f));
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, y, PANEL, PANEL);
        List<ComicScript.Line> lines = script.panels().get(i).lines();
        Map<String, Staging.Side> stage = i < stages.size() ? stages.get(i) : Map.of();
        Map<String, PanelPlacement.Head> faces = i < heads.size() ? heads.get(i) : Map.of();
        drawBubbles(g, lines == null ? List.of() : lines, names, stage, faces, y);
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
      Map<String, PanelPlacement.Head> heads,
      int panelTop) {
    g.setFont(new Font(family, Font.PLAIN, 28));
    FontMetrics fm = g.getFontMetrics();
    List<Rectangle> faces = new ArrayList<>();
    heads.values().forEach(h -> faces.add(face(h, panelTop)));
    List<Rectangle> taken = new ArrayList<>();
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
      PanelPlacement.Head head = line.speaker() == null ? null : heads.get(line.speaker());
      Area shape;
      int x;
      int y;
      if (head != null) {
        Rectangle face = face(head, panelTop);
        Rectangle box = place(face, w, h, panelTop, faces, taken);
        x = box.x;
        y = box.y;
        shape = new Area(new RoundRectangle2D.Float(x, y, w, h, 28, 28));
        Path2D toFace = tailTo(box, face);
        if (toFace != null) {
          shape.add(new Area(toFace));
        }
      } else {
        x = bubbleX(side, top, w);
        y = top ? panelTop + 16 : panelTop + PANEL - 16 - h;
        shape = new Area(new RoundRectangle2D.Float(x, y, w, h, 28, 28));
        if (side != null) {
          shape.add(new Area(tail(side, top, x, y, w, h)));
        }
      }
      taken.add(new Rectangle(x, y, w, h));
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

  /** 말풍선과 얼굴 사이 여백(꼬리가 보일 만큼). */
  private static final int FACE_GAP = 36;

  /** 얼굴 자리(합성 이미지 좌표). 머리카락까지 덮게 얼굴 높이보다 조금 크게 잡는다. */
  static Rectangle face(PanelPlacement.Head head, int panelTop) {
    int cx = MARGIN + (int) Math.round(head.x() * PANEL);
    int cy = panelTop + (int) Math.round(head.y() * PANEL);
    int r = Math.max(24, (int) Math.round(head.size() * PANEL * 0.65));
    return new Rectangle(cx - r, cy - r, 2 * r, 2 * r);
  }

  /**
   * 얼굴 쪽 말풍선 자리. 얼굴 위, 아래, 바깥 옆, 안쪽 옆 순서로 보고 컷 안에 들어가며 얼굴과 앞 말풍선을 가리지 않는 첫 자리를 쓴다. 모두 가리면 가장 적게 가리는
   * 자리다.
   */
  static Rectangle place(
      Rectangle face, int w, int h, int panelTop, List<Rectangle> faces, List<Rectangle> taken) {
    int cx = (int) face.getCenterX();
    int cy = (int) face.getCenterY();
    int x = clampX(cx - w / 2, w);
    boolean leftHalf = cx < MARGIN + PANEL / 2;
    int outX = leftHalf ? face.x - FACE_GAP - w : face.x + face.width + FACE_GAP;
    int inX = leftHalf ? face.x + face.width + FACE_GAP : face.x - FACE_GAP - w;
    List<Rectangle> options =
        List.of(
            new Rectangle(x, clampY(face.y - FACE_GAP - h, h, panelTop), w, h),
            new Rectangle(x, clampY(face.y + face.height + FACE_GAP, h, panelTop), w, h),
            new Rectangle(clampX(outX, w), clampY(cy - h / 2, h, panelTop), w, h),
            new Rectangle(clampX(inX, w), clampY(cy - h / 2, h, panelTop), w, h));
    Rectangle best = options.get(0);
    long bestCover = Long.MAX_VALUE;
    for (Rectangle o : options) {
      long cover = covered(o, faces) * 2 + covered(o, taken) * 3;
      if (cover == 0) {
        return o;
      }
      if (cover < bestCover) {
        best = o;
        bestCover = cover;
      }
    }
    return best;
  }

  private static long covered(Rectangle r, List<Rectangle> others) {
    long sum = 0;
    for (Rectangle o : others) {
      Rectangle cut = r.intersection(o);
      if (!cut.isEmpty()) {
        sum += (long) cut.width * cut.height;
      }
    }
    return sum;
  }

  private static int clampX(int x, int w) {
    return Math.max(MARGIN + 16, Math.min(MARGIN + PANEL - 16 - w, x));
  }

  private static int clampY(int y, int h, int panelTop) {
    return Math.max(panelTop + 16, Math.min(panelTop + PANEL - 16 - h, y));
  }

  /** 말풍선에서 얼굴로 가는 꼬리. 말풍선 가장자리 가운데쯤에서 나와 얼굴 가장자리 조금 앞에서 끝난다. 말풍선이 얼굴에 붙어 있어 꼬리 둘 틈이 없으면 null. */
  static Path2D tailTo(Rectangle box, Rectangle face) {
    double fx = face.getCenterX();
    double fy = face.getCenterY();
    double fr = face.width / 2.0;
    // 얼굴 가운데에서 가장 가까운 말풍선 테두리 점
    double px = Math.max(box.x, Math.min(box.x + box.width, fx));
    double py = Math.max(box.y, Math.min(box.y + box.height, fy));
    boolean horizontal = px > box.x && px < box.x + box.width;
    double dx = fx - px;
    double dy = fy - py;
    double dist = Math.hypot(dx, dy);
    double reach = Math.min(dist - fr * 0.8, TAIL * 1.6);
    if (dist == 0 || reach < 14) {
      return null;
    }
    double tipX = px + dx / dist * reach;
    double tipY = py + dy / dist * reach;
    Path2D tail = new Path2D.Float();
    if (horizontal) {
      // 위·아래 변에서 나온다. 밑변은 모서리 둥근 곳을 피한다
      double base =
          Math.max(box.x + 28, Math.min(box.x + box.width - 28 - TAIL_BASE, px - TAIL_BASE / 2.0));
      double edge = py == box.y ? box.y + 2 : box.y + box.height - 2;
      tail.moveTo(base, edge);
      tail.lineTo(tipX, tipY);
      tail.lineTo(base + TAIL_BASE, edge);
    } else {
      double base =
          Math.max(box.y + 20, Math.min(box.y + box.height - 20 - TAIL_BASE, py - TAIL_BASE / 2.0));
      double edge = px == box.x ? box.x + 2 : box.x + box.width - 2;
      tail.moveTo(edge, base);
      tail.lineTo(tipX, tipY);
      tail.lineTo(edge, base + TAIL_BASE);
    }
    tail.closePath();
    return tail;
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
