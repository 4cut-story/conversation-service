package net.java21.fourcut.conversation.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SceneCandidateTest {

  private static SceneCandidate rubric(int story, int visual, int relatable, boolean intro) {
    return new SceneCandidate(
        "FUNNY", 30, 120, "빵 하나에 40분", "반전", 0.9, null, story, visual, relatable, intro, null);
  }

  @Test
  @DisplayName("FR-004: 항목 점수로 장면 점수를 계산하고 도입부는 감점한다")
  void scored() {
    assertThat(rubric(5, 5, 5, false).scored().score()).isEqualTo(1.0);
    assertThat(rubric(5, 4, 3, false).scored().score()).isEqualTo(0.82);
    assertThat(rubric(5, 5, 5, true).scored().score()).isEqualTo(0.7);
    assertThat(rubric(1, 1, 1, true).scored().score()).isEqualTo(0.0);
    assertThat(rubric(9, 0, 3, false).scored().score()).isEqualTo(0.64);
    assertThat(rubric(5, 4, 3, false).scored().select().chosen()).isTrue();
  }

  @Test
  @DisplayName("FR-004: 항목 점수가 없는 예전 후보는 점수를 그대로 두고, CORE 종류도 받는다")
  void legacy() {
    SceneCandidate old = new SceneCandidate("IMPORTANT", 0, 60, "합격 소식", "중요", 0.8, null);
    assertThat(old.scored()).isSameAs(old);
    assertThat(new SceneCandidate("CORE", 0, 60, "이직 고민", "핵심", 0.5, null).valid()).isTrue();
    assertThat(new SceneCandidate("OTHER", 0, 60, "x", "y", 0.5, null).valid()).isFalse();
  }

  @Test
  @DisplayName("FR-004: 간직 점수가 있으면 점수에 넣어 웃음이 크게 터진 장면을 잡담보다 앞에 둔다")
  void memorable() {
    SceneCandidate big =
        new SceneCandidate(
            "FUNNY", 30, 120, "치킨 알림", "들통", 0, null, 4, 4, 4, false, null, "새벽 두 시 알림", 5);
    SceneCandidate small =
        new SceneCandidate(
            "FUNNY", 30, 120, "메뉴 고르기", "잡담", 0, null, 4, 4, 4, false, null, "뭐 먹지", 1);
    assertThat(big.scored().score()).isEqualTo(0.86);
    assertThat(small.scored().score()).isEqualTo(0.62);
    assertThat(big.scored().select().peak()).isEqualTo("새벽 두 시 알림");
    assertThat(big.scored().select().memorable()).isEqualTo(5);
  }

  @Test
  @DisplayName("FR-004: 남기고 싶은 대화(TOUCHING)와 추억(MEMORY) 종류를 받는다")
  void kinds() {
    assertThat(new SceneCandidate("TOUCHING", 0, 60, "고마움", "진심", 0.5, null).valid()).isTrue();
    assertThat(new SceneCandidate("MEMORY", 0, 60, "수학여행", "추억", 0.5, null).valid()).isTrue();
  }
}
