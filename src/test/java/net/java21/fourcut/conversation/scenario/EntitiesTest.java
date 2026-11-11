package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EntitiesTest {

  @Test
  @DisplayName("FR-005: 사전에 있는 실존 기업 이름은 일반 표현으로 바꾸고 조사를 받침에 맞춘다")
  void known() {
    assertThat(Entities.apply("삼성전자가 이번에 또 올랐대", Map.of())).isEqualTo("대기업이 이번에 또 올랐대");
    assertThat(Entities.apply("카카오는 어때? 쿠팡에서 샀어", Map.of())).isEqualTo("IT 기업은 어때? IT 기업에서 샀어");
    assertThat(Entities.apply("LG 다니는 형이 애플 주식 샀어", Map.of())).isEqualTo("대기업 다니는 형이 외국 기업 주식 샀어");
    assertThat(Entities.apply("롯데랑 한화 경기", Map.of())).isEqualTo("대기업이랑 대기업 경기");
  }

  @Test
  @DisplayName("FR-005: 다른 낱말의 일부인 말은 바꾸지 않는다")
  void boundaries() {
    String text = "삼성동 카페에서 애플파이 먹고 카카오톡 보냈어. 아이돌그룹 보이그룹 스터디그룹 걸그룹, KTX 탔어";
    assertThat(Entities.apply(text, Map.of())).isEqualTo(text);
    assertThat(Entities.remaining(text)).isEmpty();
    assertThat(Entities.apply(null, Map.of())).isNull();
    assertThat(Entities.remaining(null)).isEmpty();
  }

  @Test
  @DisplayName("FR-005: 회사 이름 꼴(…그룹, …홀딩스)은 한 회사로 바꾼다")
  void companyForm() {
    assertThat(Entities.apply("가나다그룹 회장이 왔대. 라마바홀딩스는 망했고", Map.of()))
        .isEqualTo("한 회사 회장이 왔대. 한 회사는 망했고");
    assertThat(Entities.remaining("가나다그룹 회장과 삼성")).containsExactly("삼성", "가나다그룹");
  }

  @Test
  @DisplayName("FR-005: 장면 추출이 찾은 실존 인물·기업은 대화에 나오는 것만 합치고 일반 표현으로 바꾼다")
  void found() {
    JsonMapper json = JsonMapper.builder().build();
    String spoken = "스타벅스 가자. 홍길동 회장이 그랬대";
    var nodes =
        json.readTree(
            "[{\"name\":\"스타벅스\",\"generic\":\"카페\"},{\"name\":\"홍길동 회장\",\"generic\":\"회장님\"},"
                + "{\"name\":\"없는회사\",\"generic\":\"회사\"},{\"name\":\"스타벅스\",\"generic\":\"다른 말\"},"
                + "{\"name\":\"홍길동\",\"generic\":\"\"},{\"name\":\"x\",\"generic\":\"y\"},"
                + "{\"name\":\"홍길동\",\"generic\":\"아주 아주 긴 설명이 붙은 일반 표현\"}]");
    Map<String, String> merged = Entities.merge(Map.of(), nodes, spoken);
    assertThat(merged).containsExactly(Map.entry("스타벅스", "카페"), Map.entry("홍길동 회장", "회장님"));
    assertThat(Entities.apply("스타벅스에서 홍길동 회장이 커피를", merged)).isEqualTo("카페에서 회장님이 커피를");
  }

  @Test
  @DisplayName("FR-005: 콘티 입력에는 실존 기업·인물 이름 대신 일반 표현이 들어간다")
  void prompt() {
    Transcript t =
        new Transcript(
            "ko",
            List.of("A", "B"),
            List.of(
                new Transcript.Utterance("A", 30, 31, "홍길동 회장이 삼성전자 주식 다 팔았대"),
                new Transcript.Utterance("B", 40, 41, "스타벅스 가서 얘기하자")));
    SceneCandidate scene = new SceneCandidate("FUNNY", 30, 60, "삼성 이야기", "웃김", 0.9, true);
    String prompt =
        ScriptHandler.prompt(t, scene, 0, Map.of(), Map.of("홍길동 회장", "회장님", "스타벅스", "카페"));
    assertThat(prompt)
        .doesNotContain("홍길동")
        .doesNotContain("삼성")
        .doesNotContain("스타벅스")
        .contains("회장님이 대기업 주식")
        .contains("카페 가서");
  }

  @Test
  @DisplayName("FR-005: 생성 후 검사는 콘티 제목·대사·인물 소개·줄거리에 남은 실존 기업 이름을 찾아 바꾼다")
  void script() {
    ComicScript script =
        new ComicScript(
            "삼성 다니는 형",
            List.of(new ComicScript.Character("A", "보람", "네이버 다니는 직장인", "short hair")),
            List.of(
                new ComicScript.Panel(
                    "기",
                    "wide",
                    "A talks",
                    List.of(new ComicScript.Line("A", "쿠팡 왔어", "쿠팡 왔어")),
                    "office",
                    List.of(),
                    "",
                    "none")),
            false,
            List.of(),
            null,
            "형은 카카오에 간다.",
            null);
    assertThat(script.realEntities()).contains("삼성", "네이버", "쿠팡", "카카오");
    ComicScript clean = script.generalized(s -> Entities.apply(s, Map.of()));
    assertThat(clean.realEntities()).isEmpty();
    assertThat(clean.title()).isEqualTo("대기업 다니는 형");
    assertThat(clean.characters().get(0).persona()).isEqualTo("IT 기업 다니는 직장인");
    assertThat(clean.panels().get(0).lines().get(0).text()).isEqualTo("IT 기업 왔어");
    assertThat(clean.summary()).isEqualTo("형은 IT 기업에 간다.");
  }
}
