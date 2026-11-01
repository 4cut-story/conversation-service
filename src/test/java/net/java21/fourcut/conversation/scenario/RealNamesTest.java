package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RealNamesTest {

  private static final Map<String, String> CAST = Map.of("A", "보람", "B", "지우");

  @Test
  @DisplayName("FR-005: 화자의 실명은 그 화자 가명으로, 받침에 맞게 호격·조사를 바꾼다")
  void speakerAlias() {
    Map<String, String> r = RealNames.replacements(Map.of("민지", "A", "준호", "B"), CAST);
    assertThat(RealNames.apply("역시 민지. 민지야 고마워, 민지가 다 했어", r))
        .isEqualTo("역시 보람이. 보람아 고마워, 보람이가 다 했어");
    assertThat(RealNames.apply("준호야 준호는 늦었어", r)).isEqualTo("지우야 지우는 늦었어");
  }

  @Test
  @DisplayName("FR-005: 누구인지 모르는 실명과 받침 있는 실명은 친구로 바꾼다")
  void unknown() {
    Map<String, String> r = RealNames.replacements(Map.of("민준", "", "서연", "Z"), CAST);
    assertThat(RealNames.apply("민준이가 왔어. 민준아! 서연이랑 서연", r)).isEqualTo("친구가 왔어. 친구야! 친구랑 친구");
    Map<String, String> toAlias = RealNames.replacements(Map.of("민준", "A"), CAST);
    assertThat(RealNames.apply("민준이가 민준아", toAlias)).isEqualTo("보람이가 보람아");
    assertThat(RealNames.replacements(Map.of("보람", "", "A", "", "x", ""), CAST)).isEmpty();
    assertThat(RealNames.apply(null, r)).isNull();
    assertThat(RealNames.hasFinal("")).isFalse();
    assertThat(RealNames.hasFinal("abc")).isFalse();
  }

  @Test
  @DisplayName("FR-005: 장면 추출이 구간마다 찾은 실명을 합치고, 화자를 모르던 이름에 화자를 채운다")
  void mergeNames() {
    JsonMapper json = JsonMapper.builder().build();
    var found =
        json.readTree(
            "[{\"word\":\"민지\",\"speaker\":\"\"},{\"word\":\"준호\",\"speaker\":\"Q\"},"
                + "{\"word\":\"A\",\"speaker\":\"\"},{\"word\":\"x\",\"speaker\":\"A\"}]");
    Map<String, String> first = ExtractHandler.mergeNames(Map.of(), found, List.of("A", "B"));
    assertThat(first).containsExactlyInAnyOrderEntriesOf(Map.of("민지", "", "준호", ""));
    var later = json.readTree("[{\"word\":\"민지\",\"speaker\":\"A\"},{\"word\":\"준호\"}]");
    Map<String, String> merged =
        ExtractHandler.mergeNames(new LinkedHashMap<>(first), later, List.of("A", "B"));
    assertThat(merged).containsEntry("민지", "A").containsEntry("준호", "");
  }

  @Test
  @DisplayName("FR-005: 콘티 입력은 장면 앞·장면·뒤 대화로 나누고 실명을 가명으로 바꿔 넣는다")
  void scriptPrompt() {
    Transcript t =
        new Transcript(
            "ko",
            List.of("A", "B"),
            List.of(
                new Transcript.Utterance("B", 1, 2, "민지야 여기"),
                new Transcript.Utterance("A", 40, 41, "빵 40분 기다렸어"),
                new Transcript.Utterance("B", 100, 101, "민지 최고")));
    SceneCandidate scene = new SceneCandidate("FUNNY", 30, 60, "민지의 빵", "웃김", 0.9, true);
    String prompt = ScriptHandler.prompt(t, scene, 0, Map.of("민지", "A"));
    String alias = ScriptHandler.cast(t, 0).get("A");
    assertThat(prompt).doesNotContain("민지").contains("인물 가명: A=" + alias);
    assertThat(prompt.indexOf("[장면 대화")).isLessThan(prompt.indexOf("빵 40분"));
    assertThat(prompt.indexOf("[장면 앞 대화")).isLessThan(prompt.indexOf("여기"));
    assertThat(prompt.indexOf("[장면 뒤 대화")).isLessThan(prompt.indexOf("최고"));
    assertThat(ScriptHandler.prompt(t, scene, 0, Map.of())).contains("민지야 여기");
  }
}
