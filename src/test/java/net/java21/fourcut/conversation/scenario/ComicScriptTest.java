package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComicScriptTest {

  static ComicScript script(List<ComicScript.Character> cast, List<ComicScript.Panel> panels) {
    return new ComicScript("접시 미스터리", cast, panels, null, List.of());
  }

  static List<ComicScript.Character> cast() {
    return List.of(
        new ComicScript.Character("A", "민지", "먹는 데 진심", "girl, bob hair, yellow hoodie"),
        new ComicScript.Character("B", "도윤", "능청꾼", "boy, glasses, blue shirt"));
  }

  static List<ComicScript.Panel> panels() {
    return List.of(
        new ComicScript.Panel(
            "기", "wide", "{A} and {B} sit at table, kitchen", List.of(line("A", "배고프다"))),
        new ComicScript.Panel("승", "medium", "{B} secretly eats from plate", List.of()),
        new ComicScript.Panel(
            "전", "close-up", "{A} stares at empty plate, shocked", List.of(line("A", "누구야?"))),
        new ComicScript.Panel("결", "medium", "{B} whistles, {A} chases him", List.of()));
  }

  static ComicScript.Line line(String speaker, String text) {
    return new ComicScript.Line(speaker, text);
  }

  @Test
  @DisplayName("FR-005: 가명·구도·서로 다른 장면을 갖춘 콘티는 통과하고 말풍선 이름은 가명이다")
  void valid() {
    ComicScript s = script(cast(), panels());
    assertThat(s.violation()).isNull();
    assertThat(s.displayNames()).containsEntry("A", "민지").containsEntry("B", "도윤");
  }

  @Test
  @DisplayName("FR-005: 가명이 없거나 겹치면 위반이다")
  void names() {
    var noName = List.of(new ComicScript.Character("A", " ", "", "girl"), cast().get(1));
    assertThat(script(noName, panels()).violation()).contains("가명");
    var same = List.of(cast().get(0), new ComicScript.Character("B", "민지", "", "boy"));
    assertThat(script(same, panels()).violation()).contains("겹친다");
    var noLook = List.of(new ComicScript.Character("A", "민지", "", ""), cast().get(1));
    assertThat(script(noLook, panels()).violation()).contains("외모");
  }

  @Test
  @DisplayName("FR-005: 네 컷 구도가 모두 같거나 장면이 거의 같으면 위반이다")
  void repetition() {
    var sameShot =
        panels().stream()
            .map(p -> new ComicScript.Panel(p.role(), "medium", p.scene(), p.lines()))
            .toList();
    assertThat(script(cast(), sameShot).violation()).contains("구도");
    var copy = new java.util.ArrayList<>(panels());
    copy.set(3, new ComicScript.Panel("결", "wide", "{A} and {B} sit at table, kitchen", null));
    assertThat(script(cast(), copy).violation()).contains("1번과 4번");
    var badShot = new java.util.ArrayList<>(panels());
    badShot.set(0, new ComicScript.Panel("기", "aerial", "x", null));
    assertThat(script(cast(), badShot).violation()).contains("구도(shot)");
  }

  @Test
  @DisplayName("가명이 대화 원문에 나오면 찾아낸다, 가명이 없는 예전 콘티는 화자 표시를 쓴다")
  void clashesAndFallback() {
    assertThat(script(cast(), panels()).namesFoundIn("어제 민지가 그랬잖아")).containsExactly("민지");
    var old = List.of(new ComicScript.Character("A", null, null, "girl"));
    assertThat(script(old, panels()).displayNames()).containsEntry("A", "A");
  }

  @Test
  @DisplayName("장면 단어 겹침은 Jaccard로 잰다")
  void overlap() {
    assertThat(ComicScript.overlap("a b c", "a b c")).isEqualTo(1.0);
    assertThat(ComicScript.overlap("a b", "c d")).isZero();
    assertThat(ComicScript.overlap("", "a")).isZero();
  }
}
