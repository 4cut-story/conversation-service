package net.java21.fourcut.conversation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicIdTest {

  @Test
  @DisplayName("공개 ID는 21자 URL-safe 문자열이고 매번 다르다")
  void generate() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String id = PublicId.generate();
      assertThat(id).hasSize(PublicId.LENGTH).matches("[A-Za-z0-9_-]+");
      assertThat(PublicId.isValid(id)).isTrue();
      seen.add(id);
    }
    assertThat(seen).hasSize(1000);
  }

  @Test
  @DisplayName("형식이 틀린 값(null, UUID, 길이·문자 오류)은 공개 ID가 아니다")
  void invalid() {
    assertThat(PublicId.isValid(null)).isFalse();
    assertThat(PublicId.isValid("0193a1c2-7e5b-7c3d-9a2f-5b8e4d1c6a70")).isFalse();
    assertThat(PublicId.isValid("short")).isFalse();
    assertThat(PublicId.isValid("V1StGXR8_Z5jdHi6B-myT")).isTrue();
    assertThat(PublicId.isValid("V1StGXR8_Z5jdHi6B-my!")).isFalse();
  }
}
