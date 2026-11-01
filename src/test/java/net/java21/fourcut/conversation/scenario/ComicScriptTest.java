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
  @DisplayName("FR-005: 장면 밖 대사는 버리고, 동물 컨셉은 인물마다 코드로 정한 다른 동물을 쓴다")
  void sceneLinesAndAnimals() {
    ComicScript s =
        script(cast(), panels()).withLinesWhere(t -> SpokenLines.inside(t, List.of("누구야? 내 접시!")));
    assertThat(s.panels().get(0).lines()).isEmpty();
    assertThat(s.panels().get(2).lines())
        .extracting(ComicScript.Line::text)
        .containsExactly("누구야?");
    ComicScript animals = s.withAnimals(List.of("fox", "bear"));
    assertThat(animals.characters())
        .extracting(ComicScript.Character::look)
        .containsExactly("fox, bob hair, yellow hoodie", "bear, glasses, blue shirt");
    assertThat(ScriptHandler.animals(3))
        .startsWith("bear", "cat")
        .hasSize(8)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("FR-005: 같은 대사를 두 컷에 쓰면 위반이다")
  void repeatedLine() {
    var copy = new java.util.ArrayList<>(panels());
    copy.set(
        3,
        new ComicScript.Panel(
            "결", "medium", "{B} whistles, {A} chases him", List.of(line("A", "배고 프다"))));
    assertThat(script(cast(), copy).violation()).contains("같은 대사");
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
    assertThat(ComicScript.overlap("{A} eats cake, cafe", "{B} eats cake at the cafe"))
        .isEqualTo(1.0);
    assertThat(ComicScript.overlap("{A} runs, park", "{A} sleeps, room")).isZero();
    assertThat(ComicScript.overlap("", "a")).isZero();
  }

  @Test
  @DisplayName("대사 화자를 가명으로 쓴 출력은 화자 표시로 바꿔 통과시킨다")
  void normalized() {
    var ps = new java.util.ArrayList<>(panels());
    ps.set(
        0, new ComicScript.Panel("기", "wide", "{A} and {B} at table", List.of(line("민지", "배고파"))));
    ComicScript s = script(cast(), ps);
    assertThat(s.violation()).contains("화자");
    ComicScript fixed = s.normalized();
    assertThat(fixed.violation()).isNull();
    assertThat(fixed.panels().get(0).lines().get(0).speaker()).isEqualTo("A");
  }

  @Test
  @DisplayName("FR-005: 가명이 대화에 나온 실명과 같으면 원문에 없는 가명으로 바꾸고 대사·제목도 함께 바꾼다")
  void fictionalNames() {
    var ps = new java.util.ArrayList<>(panels());
    ps.set(
        0,
        new ComicScript.Panel("기", "wide", "{A} and {B} at table", List.of(line("B", "민지야 배고파"))));
    ComicScript s = new ComicScript("민지의 치킨", cast(), ps, null, List.of());
    String spoken = "어제 민지가 치킨을 먹었대. 하늘이도 봤어";
    ComicScript fixed = s.withFictionalNames(spoken);
    assertThat(fixed.displayNames()).containsEntry("A", "지우").containsEntry("B", "도윤");
    assertThat(fixed.title()).isEqualTo("지우의 치킨");
    assertThat(fixed.panels().get(0).lines().get(0).text()).isEqualTo("지우야 배고파");
    assertThat(fixed.namesFoundIn(spoken)).isEmpty();
    assertThat(fixed.violation()).isNull();
    assertThat(s.withFictionalNames("상관없는 대화")).isSameAs(s);
  }

  @Test
  @DisplayName("FR-005: 코드로 정한 가명은 대화에 나온 이름을 피하고, LLM이 다른 이름을 내도 덮어쓴다")
  void assignedNames() {
    var names = CastNames.assign(List.of("A", "B"), "하늘이랑 지우가 왔어", 0);
    assertThat(names.values()).doesNotContain("하늘", "지우").hasSize(2).doesNotHaveDuplicates();
    assertThat(CastNames.assign(List.of("A", "B"), "", 7))
        .isEqualTo(CastNames.assign(List.of("A", "B"), "", 7));
    assertThat(CastNames.line(java.util.Map.of("A", "유진"))).isEqualTo("A=유진");
    var ps = new java.util.ArrayList<>(panels());
    ps.set(0, new ComicScript.Panel("기", "wide", "{A} at table", List.of(line("B", "민지야 안녕"))));
    ComicScript s =
        new ComicScript("t", cast(), ps, null, List.of())
            .withNames(java.util.Map.of("A", "유진", "B", "해솔"));
    assertThat(s.displayNames()).containsEntry("A", "유진").containsEntry("B", "해솔");
    assertThat(s.panels().get(0).lines().get(0).text()).isEqualTo("유진야 안녕");
    assertThat(s.withNames(java.util.Map.of())).isSameAs(s);
  }

  @Test
  @DisplayName("FR-005: nameWarnings에 적힌 실명이 대사·제목에 남아 있으면 친구로 바꾸고 JSON 조각을 지운다")
  void scrubbed() {
    var ps = new java.util.ArrayList<>(panels());
    ps.set(
        0,
        new ComicScript.Panel(
            "기", "wide", "{A} at table", List.of(line("A", "민수야 지현이가 예약 캡처했어}, {"))));
    ComicScript s =
        new ComicScript("지현의 복수", cast(), ps, null, List.of("민수", "지현이", "예약"))
            .scrubbed(w -> RealNames.called(w, "민수야 지현아 예약했어"));
    assertThat(s.panels().get(0).lines().get(0).text()).isEqualTo("친구야 친구가 예약 캡처했어");
    assertThat(s.title()).isEqualTo("친구의 복수");
  }

  @Test
  @DisplayName("FR-005: 대사를 하는 인물이 장면 설명에 없으면 그림에 나오도록 붙인다")
  void speakersInScene() {
    var ps = new java.util.ArrayList<>(panels());
    ps.set(
        1,
        new ComicScript.Panel(
            "승", "medium", "{B} looks surprised", List.of(line("A", "예약했지"), line("B", "역시"))));
    ComicScript fixed = script(cast(), ps).normalized();
    assertThat(fixed.panels().get(1).scene()).isEqualTo("{B} looks surprised, {A} nearby");
    assertThat(fixed.panels().get(0).scene()).isEqualTo(panels().get(0).scene());
    assertThat(fixed.replaced(java.util.Map.of())).isSameAs(fixed);
  }
}
