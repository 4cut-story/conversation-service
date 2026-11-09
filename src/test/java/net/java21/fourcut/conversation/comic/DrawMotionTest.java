package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Motion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DrawMotionTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final DrawHandler handler = new DrawHandler(null, null, null, json, null);

  @Test
  @DisplayName("FR-014: 움직임 컷은 두 번째 그림 edit 프롬프트와 업로드 키를 보낸다")
  void motionImage() {
    var panel =
        new ComicScript.Panel(
            "기", "wide", "{A} sits", List.of(), "night, stars", List.of(), "밤 공원", "twinkle");

    var motion = handler.motion("simple cartoon", "c1", 0, panel);

    assertThat(motion.path("type").asString()).isEqualTo("twinkle");
    assertThat(motion.path("uploadPutKey").asString()).isEqualTo("pipeline-work/c1/panel-1-b.png");
    assertThat(motion.path("prompt").asString())
        .startsWith("simple cartoon, edit this picture: keep the characters")
        .endsWith(Motion.TWINKLE.change());
  }

  @Test
  @DisplayName("FR-044: 움직임 컷은 같은 바탕에서 다르게 바꾼 세 번째 그림도 요청한다")
  void thirdFrame() {
    var panel =
        new ComicScript.Panel(
            "기", "wide", "{A} sits", List.of(), "rainy street, rain", List.of(), "비", "rain");

    var next = handler.motion("simple cartoon", "c1", 1, panel).path("next");

    assertThat(next.path("uploadPutKey").asString()).isEqualTo("pipeline-work/c1/panel-2-c.png");
    assertThat(next.path("prompt").asString())
        .startsWith("simple cartoon, edit this picture: keep the characters")
        .endsWith(Motion.RAIN.next());
    for (Motion m : Motion.values()) {
      assertThat(m.next()).isNotBlank().isNotEqualTo(m.change());
    }
  }

  @Test
  @DisplayName("FR-017: 움직임이 없는 컷은 두 번째 그림을 요청하지 않는다(첫 그림 요청은 그대로)")
  void noMotion() {
    var plain = new ComicScript.Panel("기", "wide", "{A} sits", List.of());
    assertThat(handler.motion("s", "c1", 0, plain)).isNull();
    var none =
        new ComicScript.Panel("기", "wide", "{A} sits", List.of(), "day", List.of(), "공원", "none");
    assertThat(handler.motion("s", "c1", 0, none)).isNull();
  }

  @Test
  @DisplayName("FR-030: 컷마다 완성 컷을 바탕으로 16:9 넓은 그림을 요청하고, 그림 안 글자·말풍선·테두리를 빼게 한다")
  void liveImage() {
    var panel =
        new ComicScript.Panel(
            "기", "wide", "{A} sits", List.of(), "night park, stars", List.of(), "밤 공원", "none");

    var live = handler.live(ComicProperties.DEFAULT_STYLE, "c1", 1, panel);

    assertThat(live.path("width").asInt()).isEqualTo(1024);
    assertThat(live.path("height").asInt()).isEqualTo(576);
    assertThat(live.path("uploadPutKey").asString()).isEqualTo("pipeline-work/c1/panel-2-w.png");
    assertThat(live.path("depthUploadPutKey").asString())
        .isEqualTo("pipeline-work/c1/panel-2-depth.png");
    assertThat(live.path("prompt").asString())
        .contains("wide 16:9 cinematic shot", "keep the same characters", "night park, stars")
        .contains("no text", "no speech bubbles", "no panel borders")
        .doesNotContain("simple background");
  }

  @Test
  @DisplayName("FR-032: 넓은 그림은 장면 시간대·실내외에 맞는 빛과 비·눈 공기를 더한다")
  void liveLight() {
    assertThat(DrawHandler.light("night street")).contains("street lamps");
    assertThat(DrawHandler.light("night, bedroom")).contains("lamp light in the dark room");
    assertThat(DrawHandler.light("sunset beach")).contains("golden evening light");
    assertThat(DrawHandler.light("cafe")).contains("daylight from the window");
    assertThat(DrawHandler.light("park")).contains("natural daylight");
    assertThat(DrawHandler.weather("rainy alley 빗소리")).contains("wet reflections");
    assertThat(DrawHandler.weather("snowy road")).contains("snow");
    assertThat(DrawHandler.weather("park")).isEmpty();
  }
}
