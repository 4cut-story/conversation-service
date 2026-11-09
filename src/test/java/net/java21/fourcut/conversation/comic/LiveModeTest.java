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
  @DisplayName("FR-020: 사건 효과음은 배경음 위에 한 번(반복 없음, 바로) 틀고, 없는 컷은 null이다")
  void effect() {
    var alarm =
        new SoundMatcher.Picked(
            "ccccccccccccccccccccc", "알람", "작가", "CC-BY-4.0", "l", "s", "alarm");

    var live =
        LivePlayback.build(
            json, script, List.of(), List.of(Optional.of(alarm)), List.of(false, false));

    var first = live.path("panels").get(0).path("effect");
    assertThat(first.path("publicId").asString()).isEqualTo("ccccccccccccccccccccc");
    assertThat(first.path("loop").asBoolean()).isFalse();
    assertThat(first.path("fadeMs").asInt()).isZero();
    assertThat(first.path("credit").path("license").asString()).isEqualTo("CC-BY-4.0");
    assertThat(live.path("panels").get(1).path("effect").isNull()).isTrue();
    assertThat(live.path("panels").get(0).path("sound").isNull()).isTrue();
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
    byte[] png =
        new ComicComposer()
            .livePanel(
                script,
                0,
                fill(Color.WHITE),
                Map.of(),
                Map.of(),
                List.of(new PanelPlacement.Prop(0.1, 0.1, 0.2, 0.2)));
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

  @Test
  @DisplayName("FR-031·035: 넓은 그림 컷은 16:9와 대사 자막(호칭·시간)을, 아닌 컷은 1:1을 담고 카메라는 앞뒤 컷이 다르다")
  void wide() {
    var two =
        new ComicScript(
            "t",
            List.of(
                new ComicScript.Character("A", "민지", "", "girl"),
                new ComicScript.Character("B", "하늘", "", "boy")),
            List.of(
                new ComicScript.Panel(
                    "기",
                    "wide",
                    "{A} {B}",
                    List.of(
                        new ComicScript.Line("A", "별이 많다"),
                        new ComicScript.Line("B", "  "),
                        new ComicScript.Line("C", "그러게 정말 많네")),
                    "night",
                    List.of()),
                new ComicScript.Panel("결", "wide", "{A} smiles", List.of())),
            true,
            List.of());

    var live =
        LivePlayback.build(json, two, List.of(), List.of(), List.of(false, false), List.of(true));

    var first = live.path("panels").get(0);
    assertThat(first.path("aspect").asString()).isEqualTo("16:9");
    var captions = first.path("captions");
    assertThat(captions.size()).isEqualTo(2);
    assertThat(captions.get(0).path("speaker").asString()).isEqualTo("민지");
    assertThat(captions.get(0).path("startMs").asInt()).isEqualTo(300);
    assertThat(captions.get(1).path("speaker").isNull()).isTrue();
    assertThat(captions.get(1).path("startMs").asInt())
        .isGreaterThan(captions.get(0).path("endMs").asInt());
    assertThat(captions.get(1).path("endMs").asInt()).isEqualTo(first.path("durationMs").asInt());
    var last = live.path("panels").get(1);
    assertThat(last.path("aspect").asString()).isEqualTo("1:1");
    assertThat(last.has("captions")).isFalse();
    assertThat(first.path("camera").asString()).isNotEqualTo(last.path("camera").asString());
  }

  @Test
  @DisplayName("FR-022: 소리 크기는 바탕 배경음(장소 소리·배경음악) 60%, 효과음 100%이고, 점수가 있으면 남긴다")
  void volumes() {
    var place =
        new SoundMatcher.Picked(
            "aaaaaaaaaaaaaaaaaaaaa", "빗소리", "a", "CC0-1.0", "l", "s", null, 0.72, "AMBIENCE");
    var piano =
        new SoundMatcher.Picked(
            "mmmmmmmmmmmmmmmmmmmmm", "피아노", "a", "CC0-1.0", "l", "s", null, 0.3, "MUSIC");
    var alarm =
        new SoundMatcher.Picked("ccccccccccccccccccccc", "알람", "a", "CC0-1.0", "l", "s", null);

    var live =
        LivePlayback.build(
            json,
            script,
            List.of(Optional.of(place), Optional.of(piano)),
            List.of(Optional.of(alarm)),
            List.of(false, false));

    var first = live.path("panels").get(0);
    var second = live.path("panels").get(1);
    assertThat(first.path("effect").path("volume").asDouble())
        .isGreaterThan(first.path("sound").path("volume").asDouble());
    assertThat(first.path("effect").path("volume").asDouble()).isEqualTo(1.0);
    assertThat(first.path("sound").path("volume").asDouble()).isEqualTo(0.6);
    assertThat(second.path("sound").path("volume").asDouble()).isEqualTo(0.6);
    assertThat(first.path("sound").path("score").asDouble()).isEqualTo(0.72);
    assertThat(first.path("effect").has("score")).isFalse();
  }
}
