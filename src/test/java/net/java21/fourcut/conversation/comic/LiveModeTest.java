package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.sound.SoundMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LiveModeTest {

  private final JsonMapper json = JsonMapper.builder().build();

  private final ComicScript script =
      new ComicScript(
          "t",
          List.of(new ComicScript.Character("A", "민지", "", "girl")),
          List.of(
              new ComicScript.Panel(
                  "기",
                  "wide",
                  "{A} sits",
                  List.of(new ComicScript.Line("A", "별이 정말 많다")),
                  "night, stars",
                  List.of(),
                  "밤 공원, 풀벌레",
                  "twinkle"),
              new ComicScript.Panel("결", "wide", "{A} smiles", List.of())),
          true,
          List.of());

  @Test
  @DisplayName("FR-018: 재생 정보는 컷 순서·그림·시간·소리·움직임을 담고, 소리를 못 고르거나 두 번째 그림이 없으면 null이다")
  void playback() {
    var picked =
        new SoundMatcher.Picked("aaaaaaaaaaaaaaaaaaaaa", "밤 풀벌레", "작가", "CC0-1.0", "l", "s", null);

    var live =
        LivePlayback.build(
            json, script, List.of(Optional.of(picked), Optional.empty()), List.of(true, false));

    assertThat(live.path("version").asInt()).isEqualTo(1);
    var first = live.path("panels").get(0);
    assertThat(first.path("index").asInt()).isEqualTo(1);
    assertThat(first.path("image").asString()).isEqualTo("panels/1.png");
    assertThat(first.path("durationMs").asInt()).isEqualTo(3500 + "별이 정말 많다".length() * 60);
    assertThat(first.path("sound").path("publicId").asString()).isEqualTo("aaaaaaaaaaaaaaaaaaaaa");
    assertThat(first.path("sound").path("credit").path("license").asString()).isEqualTo("CC0-1.0");
    assertThat(first.path("motion").path("type").asString()).isEqualTo("twinkle");
    assertThat(first.path("motion").path("frames").get(1).asString()).isEqualTo("panels/1-b.png");
    assertThat(first.path("motion").path("intervalMs").asInt()).isEqualTo(700);
    var last = live.path("panels").get(1);
    assertThat(last.path("sound").isNull()).isTrue();
    assertThat(last.path("motion").isNull()).isTrue();

    var notDrawn = LivePlayback.build(json, script, List.of(), List.of(false, false));
    assertThat(notDrawn.path("panels").get(0).path("motion").isNull()).isTrue();
  }

  @Test
  @DisplayName("FR-003: 보여 주는 시간은 대사 길이에 따라 3.5~8초다")
  void duration() {
    assertThat(LivePlayback.durationMs(script.panels().get(1))).isEqualTo(3500);
    var longLine =
        new ComicScript.Panel(
            "기", "wide", "x", List.of(new ComicScript.Line("A", "가".repeat(200))));
    assertThat(LivePlayback.durationMs(longLine)).isEqualTo(8000);
  }

  @Test
  @DisplayName("FR-015: 배경만 조금 바뀌면 맞고, 얼굴 자리나 그림 전체가 바뀌면 어긋난 것이다")
  void aligned() {
    BufferedImage a = fill(Color.WHITE);
    BufferedImage starry = fill(Color.WHITE);
    starry.getGraphics().fillRect(0, 0, 40, 40);
    var head = Map.of("A", new PanelPlacement.Head(0.5, 0.5, 0.2));
    assertThat(LiveFrames.aligned(a, starry, head)).isTrue();

    BufferedImage face = fill(Color.WHITE);
    var g = face.getGraphics();
    g.setColor(Color.BLACK);
    g.fillRect(150, 150, 100, 100);
    assertThat(LiveFrames.aligned(a, face, head)).isFalse();
    assertThat(LiveFrames.aligned(a, fill(Color.BLACK), Map.of())).isFalse();
  }

  @Test
  @DisplayName("FR-003: 컷 그림 한 장은 테두리까지 정사각형이고 말풍선이 들어간다")
  void livePanel() throws Exception {
    byte[] png = new ComicComposer().livePanel(script, 0, fill(Color.WHITE), Map.of(), Map.of());
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    assertThat(image.getWidth()).isEqualTo(ComicComposer.LIVE_SIZE);
    assertThat(image.getHeight()).isEqualTo(ComicComposer.LIVE_SIZE);
    // 테두리는 검다
    assertThat(image.getRGB(1, ComicComposer.LIVE_SIZE / 2) & 0xFFFFFF).isZero();
  }

  @Test
  @DisplayName("FR-008: 소리 질의는 소리 장면, 배경 첫 마디(장소), 대사다")
  void queries() {
    var q = ComposeHandler.queries(script);
    assertThat(q.get(0)).isEqualTo(new SoundMatcher.Query("밤 공원, 풀벌레", "night", "별이 정말 많다"));
    assertThat(q.get(1).soundScene()).isNull();
  }

  private static BufferedImage fill(Color c) {
    BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    var g = image.getGraphics();
    g.setColor(c);
    g.fillRect(0, 0, 400, 400);
    return image;
  }
}
