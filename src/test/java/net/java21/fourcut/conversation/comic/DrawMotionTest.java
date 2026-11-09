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
  @DisplayName("FR-017: 움직임이 없는 컷은 두 번째 그림을 요청하지 않는다(첫 그림 요청은 그대로)")
  void noMotion() {
    var plain = new ComicScript.Panel("기", "wide", "{A} sits", List.of());
    assertThat(handler.motion("s", "c1", 0, plain)).isNull();
    var none =
        new ComicScript.Panel("기", "wide", "{A} sits", List.of(), "day", List.of(), "공원", "none");
    assertThat(handler.motion("s", "c1", 0, none)).isNull();
  }
}
