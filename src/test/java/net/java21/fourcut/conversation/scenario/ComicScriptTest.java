package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

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
        .startsWith("panda", "cat")
        .hasSize(8)
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("FR-005: 대사가 빈 컷은 앞뒤 컷 대사 사이에 한 장면 발화로 채우고, 맞장구보다 긴 말을 먼저 쓴다")
  void fillsEmptyPanels() {
    List<ComicScript.Line> spoken =
        List.of(
            line("A", "배고프다"),
            line("B", "응"),
            line("B", "내가 몰래 하나 먹었지"),
            line("C", "남의 화자 말"),
            line("A", "누구야?"),
            line("B", "나 아닌데 진짜로"),
            line("A", "거짓말하지 마"));
    ComicScript s = script(cast(), panels()).withEmptyPanelsFilled(spoken);
    assertThat(s.panels())
        .allSatisfy(p -> assertThat(p.lines()).hasSize(1))
        .extracting(p -> p.lines().get(0).text())
        .containsExactly("배고프다", "내가 몰래 하나 먹었지", "누구야?", "나 아닌데 진짜로");
    assertThat(s.panels().get(1).scene()).contains("{B}");
    assertThat(s.violation()).isNull();
  }

  @Test
  @DisplayName("FR-005: 앞뒤 사이에 쓸 말이 없으면 장면의 남은 말로 채우고, 남은 말도 없으면 그대로 둔다")
  void fillsFromRestOrLeaves() {
    ComicScript s =
        script(cast(), panels())
            .withEmptyPanelsFilled(List.of(line("A", "누구야?"), line("B", "배고프다 진짜")));
    assertThat(s.panels().get(1).lines())
        .extracting(ComicScript.Line::text)
        .containsExactly("배고프다 진짜");
    assertThat(s.panels().get(3).lines()).isEmpty();
    assertThat(script(cast(), panels()).withEmptyPanelsFilled(List.of()).panels().get(1).lines())
        .isEmpty();
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

  static ComicScript.Line styled(String speaker, String text, String quote) {
    return new ComicScript.Line(speaker, text, quote);
  }

  static List<ComicScript.Panel> styledPanels() {
    return List.of(
        new ComicScript.Panel(
            "기",
            "wide",
            "{A} and {B} sit at table, inn",
            List.of(styled("A", "이 몸의 배가 비었소", "배고프다"))),
        new ComicScript.Panel(
            "승",
            "medium",
            "{B} secretly eats from plate",
            List.of(styled("B", "강호 밖의 말이오", "내일 회사 가기 싫다"))),
        new ComicScript.Panel(
            "전",
            "close-up",
            "{A} stares at empty plate, shocked",
            List.of(styled("A", "누가 본좌의 접시를 비웠소?", "누가 내 접시 먹었어?"))),
        new ComicScript.Panel(
            "결", "medium", "{B} whistles, {A} chases him", List.of(styled("B", "", "나 아니야"))));
  }

  @Test
  @DisplayName("FR-005: 컨셉 말투로 다듬은 대사는 원문(quote)으로 장면을 확인하고, 말풍선에는 다듬은 말을 쓴다")
  void styledLinesUseQuoteForMatching() {
    List<String> scene = List.of("배고프다", "누가 내 접시 먹었어?", "나 아니야");
    ComicScript s =
        script(cast(), styledPanels())
            .withLineTexts(t -> SpokenLines.snap(t, scene))
            .withLinesWhere(t -> SpokenLines.inside(t, scene))
            .withEmptyPanelsFilled(
                List.of(line("A", "배고프다"), line("B", "아 나 진짜 아니라니까"), line("A", "누가 내 접시 먹었어?")))
            .withStyledLines(SpokenLines.MAX_CHARS);

    assertThat(s.panels().get(0).lines()).containsExactly(styled("A", "이 몸의 배가 비었소", "배고프다"));
    // 원문이 장면 밖이면 다듬은 말이 그럴듯해도 버리고, 빈 컷은 장면 발화(원문)로 채운다
    assertThat(s.panels().get(1).lines())
        .containsExactly(styled("B", "아 나 진짜 아니라니까", "아 나 진짜 아니라니까"));
    assertThat(s.panels().get(2).lines().get(0).text()).isEqualTo("누가 본좌의 접시를 비웠소?");
    // 다듬은 말이 비면 원문으로 되돌려 말풍선이 비지 않는다
    assertThat(s.panels().get(3).lines()).containsExactly(styled("B", "나 아니야", "나 아니야"));
    assertThat(s.violation()).isNull();
  }

  @Test
  @DisplayName("FR-005: 다듬은 대사가 말풍선 길이를 넘으면 원문으로 되돌리고, 원문이 없는 예전 대사는 그대로 둔다")
  void styledLinesFallBack() {
    String tooLong = "본좌가 이 객잔의 모든 만두를 걸고 그대에게 정식으로 결투를 신청하는 바이오, 받으시오";
    ComicScript s =
        script(
                cast(),
                List.of(
                    new ComicScript.Panel(
                        "기", "wide", "{A} at table", List.of(styled("A", tooLong, "만두 내기 할래?"))),
                    new ComicScript.Panel("승", "medium", "{B} eats", List.of(line("B", "좋아"))),
                    new ComicScript.Panel("전", "close-up", "{A} shocked", List.of()),
                    new ComicScript.Panel("결", "medium", "{B} runs", List.of())))
            .withStyledLines(SpokenLines.MAX_CHARS);
    assertThat(s.panels().get(0).lines()).containsExactly(styled("A", "만두 내기 할래?", "만두 내기 할래?"));
    assertThat(s.panels().get(1).lines()).containsExactly(line("B", "좋아"));
    assertThat(line("B", "좋아").source()).isEqualTo("좋아");
  }

  @Test
  @DisplayName("FR-005: 실명 바꾸기는 다듬은 대사와 원문 모두에 적용한다")
  void renameAppliesToQuote() {
    ComicScript s =
        script(
                cast(),
                List.of(
                    new ComicScript.Panel(
                        "기",
                        "wide",
                        "{A} at table",
                        List.of(styled("A", "지현 소협, 어디 가오", "지현아 어디 가"))),
                    new ComicScript.Panel("승", "medium", "{B} eats", List.of()),
                    new ComicScript.Panel("전", "close-up", "{A} shocked", List.of()),
                    new ComicScript.Panel("결", "medium", "{B} runs", List.of())))
            .replaced(java.util.Map.of("지현", "도윤"))
            .normalized();
    assertThat(s.panels().get(0).lines()).containsExactly(styled("A", "도윤 소협, 어디 가오", "도윤아 어디 가"));
  }

  @Test
  @DisplayName("REQ-018: 캐릭터 이름을 쓰면 조사와 구어체 '이'를 새 이름의 받침에 맞춘다")
  void characterNameParticles() {
    java.util.Map<String, String> toFinal = java.util.Map.of("하늘", "준혁");
    assertThat(CastNames.rename("하늘아, 하늘이가 왔어. 하늘은 몰라", toFinal)).isEqualTo("준혁아, 준혁이가 왔어. 준혁은 몰라");
    java.util.Map<String, String> toOpen = java.util.Map.of("한결", "민지");
    assertThat(CastNames.rename("한결아 한결이가 한결을 한결과 한결이랑 한결으로", toOpen))
        .isEqualTo("민지야 민지가 민지를 민지와 민지랑 민지로");
    assertThat(CastNames.rename("다온아 다온이 다온는", java.util.Map.of("다온", "서율")))
        .isEqualTo("서율아 서율이 서율은");
    assertThat(CastNames.rename("하늘로 가자", java.util.Map.of("하늘", "가을"))).isEqualTo("가을로 가자");
  }

  @Test
  @DisplayName("REQ-018: 두 가명을 서로의 이름으로 바꿔도 한 번에 바뀌어 다시 바뀌지 않는다")
  void characterNameSwap() {
    assertThat(CastNames.rename("민지가 도윤을 불렀다", java.util.Map.of("민지", "도윤", "도윤", "민지")))
        .isEqualTo("도윤이 민지를 불렀다");
  }

  @Test
  @DisplayName("REQ-018: 캐릭터를 찾은 인물은 등장인물·제목·대사·원문 모두 캐릭터 이름을 쓴다")
  void withCharacterNames() {
    ComicScript s =
        new ComicScript(
            "민지의 접시",
            cast(),
            List.of(
                new ComicScript.Panel(
                    "기",
                    "wide",
                    "{A} eats",
                    List.of(new ComicScript.Line("B", "민지야 그거 내 거야", "민지야 그거 내 거"))),
                new ComicScript.Panel("승", "medium", "{B} laughs", List.of())),
            true,
            List.of("지현"));
    ComicScript named = s.withCharacterNames(java.util.Map.of("A", "서준"));
    assertThat(named.title()).isEqualTo("서준의 접시");
    assertThat(named.characters().get(0).name()).isEqualTo("서준");
    assertThat(named.characters().get(1).name()).isEqualTo("도윤");
    assertThat(named.panels().get(0).lines())
        .containsExactly(new ComicScript.Line("B", "서준아 그거 내 거야", "서준아 그거 내 거"));
    assertThat(named.nameWarnings()).containsExactly("지현");
    assertThat(named.reviewed()).isTrue();
    assertThat(named.withCharacterNames(java.util.Map.of("A", "서준"))).isEqualTo(named);
  }

  @Test
  @DisplayName("REQ-018: 다른 인물이 쓰는 이름이나 빈 이름으로는 바꾸지 않는다")
  void withCharacterNamesKeepsClash() {
    ComicScript s = script(cast(), List.of());
    assertThat(s.withCharacterNames(java.util.Map.of("A", "도윤"))).isSameAs(s);
    assertThat(s.withCharacterNames(java.util.Map.of("A", " "))).isSameAs(s);
    assertThat(s.withCharacterNames(java.util.Map.of())).isSameAs(s);
    ComicScript swapped = s.withCharacterNames(java.util.Map.of("A", "도윤", "B", "민지"));
    assertThat(swapped.characters().get(0).name()).isEqualTo("도윤");
  }

  @Test
  @DisplayName("FR-006: 컷의 배경·표정은 JSON으로 읽고, 예전 콘티에는 없어도 읽는다")
  void panelJson() {
    JsonMapper json = JsonMapper.builder().build();
    ComicScript.Panel p =
        json.readValue(
            "{\"role\":\"기\",\"shot\":\"wide\",\"scene\":\"{A} waits\",\"lines\":[],"
                + "\"setting\":\"bus stop, rain\",\"faces\":[{\"speaker\":\"A\",\"face\":\"sleepy\"}]}",
            ComicScript.Panel.class);
    assertThat(p.setting()).isEqualTo("bus stop, rain");
    assertThat(p.faces()).containsExactly(new ComicScript.Face("A", "sleepy"));
    assertThat(p.withLines(List.of()).faces()).isEqualTo(p.faces());
    assertThat(p.withScene("x").setting()).isEqualTo("bus stop, rain");
    ComicScript.Panel old =
        json.readValue(
            "{\"role\":\"기\",\"shot\":\"wide\",\"scene\":\"{A} waits\",\"lines\":[]}",
            ComicScript.Panel.class);
    assertThat(old.setting()).isNull();
    assertThat(old.faces()).isNull();
  }

  static List<ComicScript.Character> family() {
    return List.of(
        new ComicScript.Character("A", "하늘", "손이 큰 할머니", "old woman", " 할머니 ", null),
        new ComicScript.Character("B", "도윤", "장난꾸러기", "girl", "손녀", null));
  }

  @Test
  @DisplayName("REQ-018: 대사와 제목은 이름 대신 호칭으로 부르고 원문(quote)과 등장인물 이름은 그대로 둔다")
  void roleCalls() {
    ComicScript s =
        new ComicScript(
            "하늘의 비밀 레시피",
            family(),
            List.of(
                new ComicScript.Panel(
                    "기",
                    "wide",
                    "{A} cooks",
                    List.of(
                        new ComicScript.Line("B", "하늘아, 뭐 해?", "하늘아 뭐 해"),
                        new ComicScript.Line("A", "도윤이가 좋아하는 거", "도윤이가 좋아하는 거")))),
            true,
            List.of());
    ComicScript called = s.withRoleCalls();
    assertThat(called.title()).isEqualTo("할머니의 비밀 레시피");
    assertThat(called.panels().get(0).lines())
        .containsExactly(
            new ComicScript.Line("B", "할머니, 뭐 해?", "하늘아 뭐 해"),
            new ComicScript.Line("A", "손녀가 좋아하는 거", "도윤이가 좋아하는 거"));
    assertThat(called.characters().get(0).name()).isEqualTo("하늘");
    assertThat(called.callNames()).containsEntry("A", "할머니").containsEntry("B", "손녀");
    assertThat(called.withRoleCalls()).isEqualTo(called);
  }

  @Test
  @DisplayName("REQ-018: 호칭이 겹치면 번호를 붙이고, 호칭이 없거나 너무 길면 이름으로 부른다")
  void roleCallsFallback() {
    List<ComicScript.Character> friends =
        List.of(
            new ComicScript.Character("A", "하늘", "", "girl", "친구", null),
            new ComicScript.Character("B", "도윤", "", "boy", "친구", null),
            new ComicScript.Character("C", "지우", "", "boy", "아주아주아주긴호칭이다", null));
    ComicScript called = script(friends, panels()).withRoleCalls();
    assertThat(called.callNames())
        .containsEntry("A", "친구")
        .containsEntry("B", "친구2")
        .containsEntry("C", "지우");
    ComicScript old = script(cast(), panels());
    assertThat(old.withRoleCalls()).isSameAs(old);
    assertThat(old.callNames()).containsEntry("A", "민지");
  }

  @Test
  @DisplayName("REQ-018: 이름을 호칭으로 바꿀 때 조사를 맞추고 높여 부르는 호칭에는 '아/야'를 붙이지 않는다")
  void roleParticles() {
    assertThat(CastNames.toRoles("하늘아 하늘이가 하늘은 하늘을", java.util.Map.of("하늘", "형")))
        .isEqualTo("형 형이 형은 형을");
    assertThat(CastNames.toRoles("도윤아 도윤이랑 도윤이도", java.util.Map.of("도윤", "손녀")))
        .isEqualTo("손녀야 손녀랑 손녀도");
    assertThat(CastNames.toRoles("민지야, 민지가", java.util.Map.of("민지", "할머니"))).isEqualTo("할머니, 할머니가");
  }
}
