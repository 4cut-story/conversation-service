package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HandsTest {

  private static ComicScript.Panel panel(String scene, String face) {
    return new ComicScript.Panel(
        "승", "medium", scene, List.of(), "office", List.of(new ComicScript.Face("A", face)));
  }

  private static ComicScript script(ComicScript.Panel... panels) {
    return new ComicScript(
        "t",
        List.of(new ComicScript.Character("A", "태나", "팀장", "woman, long brown hair")),
        List.of(panels),
        true,
        List.of());
  }

  @Test
  @DisplayName("FR-006: 타자 치는 장면에 표정 칸의 턱 괸 손이 붙으면 손 자세를 빼고 표정만 남긴다(손이 셋)")
  void faceGestureDroppedWhenSceneUsesHands() {
    var p = Hands.spare(panel("{A} types on {노트북}", "frowning, hand on chin"));
    assertThat(p.scene()).isEqualTo("{A} types on {노트북}");
    assertThat(p.faces().get(0).face()).isEqualTo("frowning");
  }

  @Test
  @DisplayName("FR-006: 표정 칸이 손 자세뿐이면 비운다")
  void faceOnlyGestureBecomesNull() {
    var p = Hands.spare(panel("{A} holds {컵} by its handle", "hands on cheeks"));
    assertThat(p.faces().get(0).face()).isNull();
  }

  @Test
  @DisplayName("FR-006: 장면이 손을 쓰지 않으면 표정 칸의 손 자세를 그대로 둔다")
  void faceGestureKeptWhenHandsFree() {
    var p = panel("{A} stands by the window", "wide-eyed, hands on cheeks");
    assertThat(Hands.spare(p)).isSameAs(p);
  }

  @Test
  @DisplayName("FR-006: 장면에 손 쓰는 동작과 턱 괴기가 함께 있으면 턱 괴기를 뺀다")
  void restClauseDropped() {
    assertThat(Hands.action("{A} types on {노트북}, chin resting on a hand"))
        .isEqualTo("{A} types on {노트북}");
    assertThat(Hands.action("{A} types on {노트북} while resting the chin on a fist"))
        .isEqualTo("{A} types on {노트북}");
    assertThat(Hands.action("{A} holds {컵} and puts a hand on the hip")).isEqualTo("{A} holds {컵}");
  }

  @Test
  @DisplayName("FR-006: 턱 괴기가 주 동작이면 뒤의 손 쓰는 동작을 앞 인물에 붙인다")
  void leadingRestReplaced() {
    assertThat(Hands.action("{A} rests chin on a fist, typing on {노트북}"))
        .isEqualTo("{A} typing on {노트북}");
    assertThat(Hands.action("rests chin on a fist, typing on {노트북}"))
        .isEqualTo("rests chin on a fist, typing on {노트북}");
  }

  @Test
  @DisplayName("FR-006: 손 자세 하나뿐이거나 손 쓰는 동작이 없으면 장면을 그대로 둔다")
  void untouched() {
    assertThat(Hands.action("{A} puts fists on the hips, smiling"))
        .isEqualTo("{A} puts fists on the hips, smiling");
    assertThat(Hands.action("{A} and {B} hold {상자}")).isEqualTo("{A} and {B} hold {상자}");
    assertThat(Hands.action("{A} types on {노트북}")).isEqualTo("{A} types on {노트북}");
  }

  @Test
  @DisplayName("FR-006: 콘티 전체에서 바뀐 컷만 고치고, 바뀐 것이 없으면 같은 콘티를 돌려준다")
  void wholeScript() {
    var fine = script(panel("{A} stands by the desk", "smile"));
    assertThat(Hands.spare(fine)).isSameAs(fine);
    var three = script(panel("{A} types on {노트북}", "narrowed eyes, hand on chin"));
    var fixed = Hands.spare(three);
    assertThat(fixed.panels().get(0).faces().get(0).face()).isEqualTo("narrowed eyes");
    assertThat(fixed.title()).isEqualTo("t");
    var empty = new ComicScript("t", List.of(), null, true, List.of());
    assertThat(Hands.spare(empty)).isSameAs(empty);
    assertThat(Hands.spare((ComicScript.Panel) null)).isNull();
    assertThat(Hands.face(null)).isNull();
  }
}
