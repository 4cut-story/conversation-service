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
  @DisplayName("FR-007: 문장부호만 다음 줄로 넘어가지 않는다")
  void wrapKeepsPunctuation() {
    Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB).createGraphics();
    g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 20));
    FontMetrics fm = g.getFontMetrics();
    List<String> rows = ComicComposer.wrap("가나다라마?", fm, fm.stringWidth("가나다라마"));
    assertThat(rows).containsExactly("가나다라마?");
    rows = ComicComposer.wrap("가나다라마바사아자차…", fm, fm.stringWidth("가나다라마"));
    assertThat(rows).containsExactly("가나다라마", "바사아자차…");
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

  @Test
  @DisplayName("REQ-018: 등장인물 소개는 호칭, 캐릭터 이름, 캐릭터 모습을 넘긴다")
  void cast() {
    ComicScript s =
        new ComicScript(
            "t",
            java.util.List.of(
                new ComicScript.Character("A", "하늘", "", "old woman", "할머니", "look1"),
                new ComicScript.Character("B", "도윤", "", "girl")),
            java.util.List.of(),
            true,
            java.util.List.of());
    assertThat(ComposeHandler.cast(s))
        .containsExactly(
            new ContentClient.CastMember("할머니", "하늘", "look1", null),
            new ContentClient.CastMember("도윤", "도윤", null, null));
  }

  @Test
  @DisplayName("FR-007: 얼굴을 알면 말풍선은 얼굴 위에 두고, 위에 자리가 없으면 아래에 두며 얼굴을 가리지 않는다")
  void placeAtFace() {
    int top = 100;
    var high = ComicComposer.face(new PanelPlacement.Head(0.3, 0.1, 0.15), top);
    var low = ComicComposer.face(new PanelPlacement.Head(0.7, 0.7, 0.15), top);
    List<java.awt.Rectangle> faces = List.of(high, low);
    var below = ComicComposer.place(high, 260, 90, top, faces, List.of());
    assertThat(below.intersects(high)).isFalse();
    assertThat(below.y).isGreaterThan(high.y);
    var above = ComicComposer.place(low, 260, 90, top, faces, List.of());
    assertThat(above.intersects(low)).isFalse();
    assertThat(above.y + above.height).isLessThan(low.y);
    // 앞 말풍선이 차지한 자리는 피한다
    var next = ComicComposer.place(low, 260, 90, top, faces, List.of(above));
    assertThat(next.intersects(above)).isFalse();
    assertThat(next.intersects(low)).isFalse();
  }

  @Test
  @DisplayName("FR-007: 얼굴 자리는 받은 얼굴 높이의 두 배에 머리카락 쪽으로 더 넓혀, 얼굴 위 말풍선이 눈과 머리를 가리지 않는다")
  void faceBoxCoversHair() {
    int top = 100;
    int panel = ComicComposer.PANEL;
    var head = new PanelPlacement.Head(0.5, 0.45, 0.15);
    var face = ComicComposer.face(head, top);
    int cy = top + (int) Math.round(0.45 * panel);
    int size = (int) Math.round(0.15 * panel);
    // 얼굴 가운데에서 위로 얼굴 높이보다 더 덮는다(답한 가운데가 입 쪽이어도 이마·머리카락까지)
    assertThat(cy - face.y).isGreaterThan(size);
    assertThat(face.width).isGreaterThan(size);
    assertThat(face.height).isGreaterThanOrEqualTo(2 * size);
    var above = ComicComposer.place(face, 400, 80, top, List.of(face), List.of());
    assertThat(above.y + above.height).isLessThan(face.y);
    // 얼굴 위 말풍선은 얼굴에서 떨어져 꼬리가 얼굴 안으로 들어가지 않는다
    var tail = ComicComposer.tailTo(above, face).getBounds();
    assertThat(tail.y + tail.height).isLessThan(cy);
  }

  @Test
  @DisplayName("FR-007: 얼굴이 크게 그려져 넓은 말풍선이 들어갈 빈 자리가 없으면, 좁힌 말풍선은 얼굴 옆 빈 자리를 찾는다")
  void narrowBubbleFitsBesideBigFace() {
    int top = 100;
    int panel = ComicComposer.PANEL;
    var face = ComicComposer.face(new PanelPlacement.Head(0.5, 0.55, 0.3), top);
    var wide = ComicComposer.clear(face, (int) (panel * 0.72), 160, top, List.of(face), List.of());
    assertThat(wide).isNull();
    var narrow = ComicComposer.clear(face, 100, 150, top, List.of(face), List.of());
    assertThat(narrow).isNotNull();
    assertThat(narrow.intersects(face)).isFalse();
    // 빈 자리가 없어도 place는 가장 덜 가리는 자리를 준다
    assertThat(ComicComposer.place(face, (int) (panel * 0.72), 160, top, List.of(face), List.of()))
        .isNotNull();
  }

  @Test
  @DisplayName("FR-007: 얼굴 위에 자리가 없으면 몸을 덮는 얼굴 아래보다 얼굴 옆 빈 벽을 먼저 쓴다")
  void prefersWallOverBody() {
    int top = 100;
    var face = ComicComposer.face(new PanelPlacement.Head(0.5, 0.3, 0.2), top);
    var body = ComicComposer.body(face, top);
    assertThat(body.y).isEqualTo(face.y + face.height);
    assertThat(body.y + body.height).isEqualTo(top + ComicComposer.PANEL);
    List<java.awt.Rectangle> avoid = List.of(face, body);
    var box = ComicComposer.clear(face, 220, 150, top, avoid, List.of());
    assertThat(box).isNotNull();
    assertThat(box.intersects(face)).isFalse();
    assertThat(box.intersects(body)).isFalse();
    assertThat(box.y).isLessThan(face.y + face.height);
  }

  @Test
  @DisplayName("FR-007: 그림에서 본 물건(그릇·휴대폰 등)도 얼굴처럼 말풍선이 가리지 않는다")
  void avoidsProps() {
    int top = 100;
    var face = ComicComposer.face(new PanelPlacement.Head(0.5, 0.1, 0.15), top);
    var bowl = ComicComposer.prop(new PanelPlacement.Prop(0.25, 0.4, 0.5, 0.15), top);
    var free = ComicComposer.place(face, 300, 80, top, List.of(face), List.of());
    assertThat(free.intersects(bowl)).isTrue();
    var b = ComicComposer.place(face, 300, 80, top, List.of(face, bowl), List.of());
    assertThat(b.intersects(bowl)).isFalse();
    assertThat(b.intersects(face)).isFalse();
  }

  @Test
  @DisplayName("FR-007: 꼬리는 말풍선에서 얼굴 쪽으로 나오고, 붙어 있으면 그리지 않는다")
  void tailToFace() {
    var face = new java.awt.Rectangle(300, 400, 100, 100);
    var box = new java.awt.Rectangle(250, 200, 200, 100);
    var tail = ComicComposer.tailTo(box, face).getBounds();
    assertThat(tail.y + tail.height).isGreaterThan(box.y + box.height + 10);
    assertThat(tail.y + tail.height).isLessThan(face.y + 20);
    var side = new java.awt.Rectangle(500, 400, 150, 100);
    var sideTail = ComicComposer.tailTo(side, face).getBounds();
    assertThat(sideTail.x).isLessThan(side.x - 10);
    assertThat(ComicComposer.tailTo(new java.awt.Rectangle(300, 300, 100, 99), face)).isNull();
  }

  @Test
  @DisplayName("FR-007: 얼굴 위치를 받아도 네 컷을 한 장으로 합친다")
  void composeWithHeads() throws Exception {
    ComicScript.Panel panel =
        new ComicScript.Panel(
            "기",
            "wide",
            "장면",
            List.of(new ComicScript.Line("A", "안녕"), new ComicScript.Line("B", "응 안녕 반가워")));
    ComicScript script =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "민지", "", "girl"),
                new ComicScript.Character("B", "도윤", "", "boy")),
            List.of(panel, panel, panel, panel),
            true,
            List.of());
    var heads =
        java.util.Map.of(
            "A",
            new PanelPlacement.Head(0.3, 0.4, 0.2),
            "B",
            new PanelPlacement.Head(0.7, 0.4, 0.2));
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    ComicComposer.Result result =
        composer.compose(
            script,
            List.of(image, image, image, image),
            Staging.panels(script),
            List.of(heads, java.util.Map.of(), heads, heads),
            List.of(List.of(new PanelPlacement.Prop(0.4, 0.6, 0.2, 0.2))));
    assertThat(ImageIO.read(new ByteArrayInputStream(result.full())).getWidth())
        .isEqualTo(ComicComposer.WIDTH);
  }

  @Test
  @DisplayName("FR-007: 컷에 안 보이는 화자의 말풍선은 보이는 얼굴과 앞 말풍선을 피한 모서리에 둔다")
  void offscreenBubble() {
    int top = 100;
    var face = ComicComposer.face(new PanelPlacement.Head(0.5, 0.35, 0.3), top);
    var first = ComicComposer.place(face, 300, 90, top, List.of(face), List.of());
    var other =
        ComicComposer.offscreen(Staging.Side.RIGHT, 300, 90, top, List.of(face), List.of(first));
    assertThat(other.intersects(face)).isFalse();
    assertThat(other.intersects(first)).isFalse();
    var left = ComicComposer.offscreen(Staging.Side.LEFT, 200, 80, top, List.of(), List.of());
    var right = ComicComposer.offscreen(Staging.Side.RIGHT, 200, 80, top, List.of(), List.of());
    assertThat(left.x).isLessThan(right.x);
    assertThat(left.y).isEqualTo(right.y);
  }

  @Test
  @DisplayName("FR-007: 뒤 대사의 말풍선은 앞 말풍선보다 위에 두지 않아 위에서 아래로 대사 순서대로 읽힌다")
  void readingOrder() {
    int top = 100;
    var first = ComicComposer.face(new PanelPlacement.Head(0.3, 0.55, 0.15), top);
    var second = ComicComposer.face(new PanelPlacement.Head(0.7, 0.25, 0.15), top);
    List<java.awt.Rectangle> faces = List.of(first, second);
    var a = ComicComposer.place(first, 260, 90, top, faces, List.of());
    var b = ComicComposer.place(second, 260, 90, top, faces, List.of(a));
    assertThat(ComicComposer.readsAfter(b, a)).isTrue();
    assertThat(b.intersects(a)).isFalse();
  }

  @Test
  @DisplayName("FR-007: 앞 말풍선이 컷 아래에 있으면 순서보다 가리지 않는 것을 먼저 지킨다")
  void neverHidesEarlierBubble() {
    int top = 100;
    int panel = ComicComposer.PANEL;
    var face = ComicComposer.face(new PanelPlacement.Head(0.5, 0.4, 0.15), top);
    var low =
        new java.awt.Rectangle(ComicComposer.MARGIN + 40, top + panel - 16 - 100, panel - 80, 100);
    var b = ComicComposer.place(face, 400, 90, top, List.of(face), List.of(low));
    assertThat(b.intersects(low)).isFalse();
  }
}
