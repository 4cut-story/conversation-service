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
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.springframework.stereotype.Component;

/**
 * 세로 만화 합성(컷 4~10개를 위에서 아래로). 그림 위에 말풍선을 코드로 그려 한글이 깨지지 않게 한다 (FR-007). 글꼴은 Noto Sans CJK(이미지에 설치)를
 * 먼저 찾는다.
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

  static final int MARGIN = 24;
  private static final int GAP = 20;
  private static final int TITLE_HEIGHT = 90;
  static final int PANEL = WIDTH - 2 * MARGIN;
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
    return compose(script, panels, stages, heads, List.of());
  }

  /** props: 컷별 가리면 안 되는 물건(그림에서 본 상자). 말풍선이 얼굴과 함께 피한다. */
  Result compose(
      ComicScript script,
      List<BufferedImage> panels,
      List<Map<String, Staging.Side>> stages,
      List<Map<String, PanelPlacement.Head>> heads,
      List<List<PanelPlacement.Prop>> props) {
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
        List<PanelPlacement.Prop> things = i < props.size() ? props.get(i) : List.of();
        drawBubbles(g, lines == null ? List.of() : lines, names, stage, faces, things, y);
        y += PANEL + GAP;
      }
    } finally {
      g.dispose();
    }
    return new Result(png(out), png(scale(out, THUMB_WIDTH)));
  }

  /** 라이브 모드 컷 그림 한 변(테두리 포함). */
  public static final int LIVE_SIZE = PANEL + 4;

  /**
   * 라이브 모드 컷 그림 한 장: 세로 만화와 같은 말풍선을 같은 자리에 넣고 테두리까지 자른 PNG(LIVE_SIZE 정사각형). 두 번째 그림도 첫 그림의
   * 자리(stage, heads)로 그려 말풍선이 움직이지 않는다.
   */
  byte[] livePanel(
      ComicScript script,
      int index,
      BufferedImage panel,
      Map<String, Staging.Side> stage,
      Map<String, PanelPlacement.Head> heads) {
    return livePanel(script, index, panel, stage, heads, List.of());
  }

  /** 가리면 안 되는 물건(props)까지 피하는 라이브 모드 컷 그림. */
  byte[] livePanel(
      ComicScript script,
      int index,
      BufferedImage panel,
      Map<String, Staging.Side> stage,
      Map<String, PanelPlacement.Head> heads,
      List<PanelPlacement.Prop> props) {
    int height = PANEL + 2 * MARGIN;
    BufferedImage out = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      quality(g);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, WIDTH, height);
      g.drawImage(panel, MARGIN, MARGIN, PANEL, PANEL, null);
      g.setStroke(new BasicStroke(4f));
      g.setColor(Color.BLACK);
      g.drawRect(MARGIN, MARGIN, PANEL, PANEL);
      List<ComicScript.Line> lines = script.panels().get(index).lines();
      drawBubbles(
          g,
          lines == null ? List.of() : lines,
          script.callNames(),
          stage == null ? Map.of() : stage,
          heads == null ? Map.of() : heads,
          props == null ? List.of() : props,
          MARGIN);
    } finally {
      g.dispose();
    }
    return png(out.getSubimage(MARGIN - 2, MARGIN - 2, LIVE_SIZE, LIVE_SIZE));
  }

  private void drawBubbles(
      Graphics2D g,
      List<ComicScript.Line> lines,
      Map<String, String> names,
      Map<String, Staging.Side> stage,
      Map<String, PanelPlacement.Head> heads,
      List<PanelPlacement.Prop> props,
      int panelTop) {
    g.setFont(new Font(family, Font.PLAIN, 28));
    FontMetrics fm = g.getFontMetrics();
    // 가리면 안 되는 자리: 얼굴과 물건. 말풍선 자리를 고를 때 똑같이 피한다
    List<Rectangle> faces = new ArrayList<>();
    heads.values().forEach(h -> faces.add(face(h, panelTop)));
    props.forEach(p -> faces.add(prop(p, panelTop)));
    List<Rectangle> taken = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      ComicScript.Line line = lines.get(i);
      Staging.Side side = line.speaker() == null ? null : stage.get(line.speaker());
      boolean top = i % 2 == 0;
      int max =
          side == Staging.Side.LEFT || side == Staging.Side.RIGHT ? BUBBLE_MAX_SIDE : BUBBLE_MAX;
      String text = bubbleText(names.getOrDefault(line.speaker(), line.speaker()), line);
      List<String> rows = wrap(text, fm, max - 2 * BUBBLE_PAD);
      int w = rows.stream().mapToInt(fm::stringWidth).max().orElse(0) + 2 * BUBBLE_PAD;
      int h = rows.size() * fm.getHeight() + 2 * BUBBLE_PAD;
      PanelPlacement.Head head = line.speaker() == null ? null : heads.get(line.speaker());
      Area shape;
      int x;
      int y;
      if (head != null) {
        Rectangle face = face(head, panelTop);
        Rectangle box = clear(face, w, h, panelTop, faces, taken);
        // 넓은 말풍선이 들어갈 빈 자리가 없으면(얼굴이 크게 그려진 컷) 폭을 좁혀 줄을 늘린 말풍선으로 빈 자리를 다시 찾는다
        for (int n = 0; box == null && n < NARROW.length; n++) {
          int limit = (int) (PANEL * NARROW[n]);
          if (limit >= w) {
            continue;
          }
          List<String> narrow = wrap(text, fm, limit - 2 * BUBBLE_PAD);
          int nw = narrow.stream().mapToInt(fm::stringWidth).max().orElse(0) + 2 * BUBBLE_PAD;
          int nh = narrow.size() * fm.getHeight() + 2 * BUBBLE_PAD;
          Rectangle fit = clear(face, nw, nh, panelTop, faces, taken);
          if (fit != null) {
            rows = narrow;
            w = nw;
            h = nh;
            box = fit;
          }
        }
        if (box == null) {
          box = place(face, w, h, panelTop, faces, taken);
        }
        x = box.x;
        y = box.y;
        shape = new Area(new RoundRectangle2D.Float(x, y, w, h, 28, 28));
        Path2D toFace = tailTo(box, face);
        if (toFace != null) {
          shape.add(new Area(toFace));
        }
      } else if (!heads.isEmpty()) {
        // 그림에 얼굴이 보이는데 이 화자만 없으면 컷 밖에서 하는 말이다. 보이는 사람을 가리키면 그 사람 말로 읽혀서(국밥집 4컷), 얼굴과 앞
        // 말풍선을 피한 모서리에 꼬리 없이 둔다
        Rectangle box = offscreen(side, w, h, panelTop, faces, taken);
        x = box.x;
        y = box.y;
        shape = new Area(new RoundRectangle2D.Float(x, y, w, h, 28, 28));
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

  /**
   * 얼굴 위 말풍선과 얼굴 사이 여백. 그림을 읽는 모델이 얼굴 가운데를 실제보다 아래(입·턱 쪽)로 답하는 일이 잦아, 얼굴 바로 위에 붙이면 눈을 가렸다(팀장 4컷
   * 2·3컷). 위쪽은 넉넉히 띄운다.
   */
  private static final int ABOVE_GAP = 72;

  /** 물건 상자 둘레 여백. */
  private static final int PROP_PAD = 8;

  /**
   * 얼굴 자리(합성 이미지 좌표). 받은 얼굴 높이를 그대로 반지름으로 써서 얼굴 높이의 두 배로 잡고, 머리카락과 위치 오차를 덮게 위로 반지름의 절반(최대 48px)을 더
   * 늘린다. 반지름을 얼굴 높이의 0.65배로 잡던 때는 말풍선이 눈을 가렸다.
   */
  static Rectangle face(PanelPlacement.Head head, int panelTop) {
    int cx = MARGIN + (int) Math.round(head.x() * PANEL);
    int cy = panelTop + (int) Math.round(head.y() * PANEL);
    int r = Math.max(32, (int) Math.round(head.size() * PANEL));
    int up = Math.min(r / 2, 48);
    return new Rectangle(cx - r, cy - r - up, 2 * r, 2 * r + up);
  }

  /** 물건 자리(합성 이미지 좌표). 둘레를 조금 넓힌다. */
  static Rectangle prop(PanelPlacement.Prop prop, int panelTop) {
    int x = MARGIN + (int) Math.round(prop.x() * PANEL) - PROP_PAD;
    int y = panelTop + (int) Math.round(prop.y() * PANEL) - PROP_PAD;
    int w = (int) Math.round(prop.w() * PANEL) + 2 * PROP_PAD;
    int h = (int) Math.round(prop.h() * PANEL) + 2 * PROP_PAD;
    return new Rectangle(x, y, w, h);
  }

  /**
   * 얼굴 쪽 말풍선 자리. 얼굴 위, 아래, 바깥 옆, 안쪽 옆 순서로 보고 컷 안에 들어가며 얼굴과 앞 말풍선을 가리지 않는 첫 자리를 쓴다. 모두 가리면 가장 적게 가리는
   * 자리다.
   */
  static Rectangle place(
      Rectangle face, int w, int h, int panelTop, List<Rectangle> faces, List<Rectangle> taken) {
    Rectangle free = clear(face, w, h, panelTop, faces, taken);
    return free != null ? free : pick(options(face, w, h, panelTop, taken), faces, taken);
  }

  /** 얼굴 쪽 말풍선 후보 자리: 얼굴 위, 아래, 바깥 옆, 안쪽 옆, 앞 말풍선 아래. */
  private static List<Rectangle> options(
      Rectangle face, int w, int h, int panelTop, List<Rectangle> taken) {
    int cx = (int) face.getCenterX();
    int cy = (int) face.getCenterY();
    int x = clampX(cx - w / 2, w);
    boolean leftHalf = cx < MARGIN + PANEL / 2;
    int outX = leftHalf ? face.x - FACE_GAP - w : face.x + face.width + FACE_GAP;
    int inX = leftHalf ? face.x + face.width + FACE_GAP : face.x - FACE_GAP - w;
    return List.of(
        new Rectangle(x, clampY(face.y - ABOVE_GAP - h, h, panelTop), w, h),
        new Rectangle(x, clampY(face.y + face.height + FACE_GAP, h, panelTop), w, h),
        new Rectangle(clampX(outX, w), clampY(cy - h / 2, h, panelTop), w, h),
        new Rectangle(clampX(inX, w), clampY(cy - h / 2, h, panelTop), w, h),
        belowLast(x, w, h, panelTop, taken),
        belowLast(MARGIN + 16, w, h, panelTop, taken),
        belowLast(MARGIN + PANEL - 16 - w, w, h, panelTop, taken));
  }

  /** 좁힌 말풍선 폭(컷 폭에 대한 비율). 넓은 말풍선이 들어갈 빈 자리가 없을 때 차례로 써 본다. */
  private static final double[] NARROW = {0.5, 0.38};

  /**
   * 얼굴·물건·앞 말풍선을 하나도 가리지 않고 대사 순서대로 읽히는 자리. 정해 둔 후보(얼굴 위, 아래, 옆…)를 먼저 보고, 모두 걸리면 컷 전체를 훑어 화자 얼굴에 가장
   * 가까운 곳이다. 그런 자리가 없으면 null.
   */
  static Rectangle clear(
      Rectangle face, int w, int h, int panelTop, List<Rectangle> faces, List<Rectangle> taken) {
    for (Rectangle o : options(face, w, h, panelTop, taken)) {
      if (cost(o, faces, taken) == 0) {
        return o;
      }
    }
    return nearest(face, w, h, panelTop, faces, taken);
  }

  /** 빈 자리를 찾을 때 훑는 간격(픽셀). */
  private static final int SCAN = 16;

  /**
   * 정해 둔 자리가 모두 얼굴·물건·앞 말풍선에 걸릴 때, 컷 전체를 훑어 아무것도 가리지 않고 대사 순서대로 읽히는 자리 가운데 화자 얼굴에 가장 가까운 자리. 얼굴에 너무
   * 붙으면 꼬리 둘 틈이 없어 뺀다. 없으면 null.
   */
  static Rectangle nearest(
      Rectangle face, int w, int h, int panelTop, List<Rectangle> faces, List<Rectangle> taken) {
    Rectangle best = null;
    double bestDist = Double.MAX_VALUE;
    for (int y = panelTop + 16; y <= panelTop + PANEL - 16 - h; y += SCAN) {
      for (int x = MARGIN + 16; x <= MARGIN + PANEL - 16 - w; x += SCAN) {
        Rectangle r = new Rectangle(x, y, w, h);
        if (cost(r, faces, taken) != 0) {
          continue;
        }
        double dist = gap(r, face);
        if (dist >= FACE_GAP / 2.0 && dist < bestDist) {
          best = r;
          bestDist = dist;
        }
      }
    }
    return best;
  }

  /** 두 상자 사이 가장 가까운 거리(겹치면 0). */
  private static double gap(Rectangle a, Rectangle b) {
    int dx = Math.max(0, Math.max(b.x - (a.x + a.width), a.x - (b.x + b.width)));
    int dy = Math.max(0, Math.max(b.y - (a.y + a.height), a.y - (b.y + b.height)));
    return Math.hypot(dx, dy);
  }

  /**
   * 컷 밖 화자의 말풍선 자리. 화자 쪽 위 모서리, 반대쪽 위, 화자 쪽 아래, 반대쪽 아래 순서로 보고 얼굴과 앞 말풍선을 가리지 않는 첫 자리를 쓴다. 모두 가리면 가장
   * 적게 가리는 자리다.
   */
  static Rectangle offscreen(
      Staging.Side side, int w, int h, int panelTop, List<Rectangle> faces, List<Rectangle> taken) {
    int left = MARGIN + 16;
    int right = MARGIN + PANEL - 16 - w;
    int up = panelTop + 16;
    int down = panelTop + PANEL - 16 - h;
    boolean rightFirst = side == Staging.Side.RIGHT;
    int near = rightFirst ? right : left;
    int far = rightFirst ? left : right;
    List<Rectangle> options =
        List.of(
            new Rectangle(near, up, w, h),
            new Rectangle(far, up, w, h),
            new Rectangle(near, down, w, h),
            new Rectangle(far, down, w, h));
    return pick(options, faces, taken);
  }

  /** 앞 말풍선 바로 아래 자리 (앞 말풍선이 없으면 컷 위). */
  private static Rectangle belowLast(int x, int w, int h, int panelTop, List<Rectangle> taken) {
    int y = taken.isEmpty() ? panelTop + 16 : taken.getLast().y + taken.getLast().height + 12;
    return new Rectangle(x, clampY(y, h, panelTop), w, h);
  }

  /**
   * 자리 후보 중 얼굴과 앞 말풍선을 가리지 않고 앞 말풍선 다음에 읽히는 첫 자리. 말풍선은 위에서 아래로, 같은 줄이면 왼쪽에서 오른쪽으로 읽으므로 앞 말풍선보다 위에
   * 두면 대사 순서가 거꾸로 읽힌다(9회차 키오스크 1·4·5컷). 모두 걸리면 가장 덜 걸리는 자리다.
   */
  private static Rectangle pick(
      List<Rectangle> options, List<Rectangle> faces, List<Rectangle> taken) {
    Rectangle best = options.get(0);
    long bestCover = Long.MAX_VALUE;
    for (Rectangle o : options) {
      long cover = cost(o, faces, taken);
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

  /** 자리의 나쁨: 얼굴·물건과 앞 말풍선을 가린 넓이, 앞 말풍선을 가리거나 대사 순서가 거꾸로 읽히면 큰 벌점. 0이면 문제없는 자리다. */
  private static long cost(Rectangle o, List<Rectangle> faces, List<Rectangle> taken) {
    long hidden = covered(o, taken);
    long cover = covered(o, faces) * 2 + hidden * 3;
    if (hidden > 0) {
      // 앞 말풍선 글을 가리면 읽을 수 없다. 순서가 바뀌는 것보다 나쁘다(회차 11 love-07·funny-12 3컷)
      cover += 4L * PANEL * PANEL;
    }
    if (!taken.isEmpty() && !readsAfter(o, taken.getLast())) {
      cover += (long) PANEL * PANEL;
    }
    return cover;
  }

  /** 앞 말풍선 다음에 읽히는 자리인지: 더 아래이거나, 거의 같은 높이에서 오른쪽. */
  static boolean readsAfter(Rectangle next, Rectangle previous) {
    int row = 24;
    return next.y >= previous.y + row
        || Math.abs(next.y - previous.y) < row && next.x >= previous.x + previous.width / 2;
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
    double reach = Math.min(dist - fr * 0.8, TAIL * 2.0);
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
      String last = row.toString().strip();
      if (!rows.isEmpty() && PUNCTUATION_ONLY.matcher(last).matches()) {
        // 문장부호만 다음 줄로 넘어가지 않게 앞 줄 끝에 붙인다
        rows.set(rows.size() - 1, rows.getLast() + last);
      } else {
        rows.add(last);
      }
    }
    return rows;
  }

  private static final Pattern PUNCTUATION_ONLY = Pattern.compile("[.,?!…~·\"'”’)\\]]+");

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
