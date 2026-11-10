package net.java21.fourcut.conversation.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 서비스에 넣은 목소리 목록(voices/voices.json)이 voices 표에 들어갈 수 있는지. */
class VoiceDataTest {

  private final List<VoiceLibrary.Entry> entries =
      new VoiceLibrary(null, null, JsonMapper.builder().build(), false).load();

  @Test
  @DisplayName("목소리 목록: ID가 겹치지 않고 성별·나이대·사투리 코드가 표의 제약에 맞는다")
  void codes() {
    Set<String> ids = new HashSet<>();
    assertThat(entries).isNotEmpty();
    for (VoiceLibrary.Entry e : entries) {
      assertThat(ids.add(e.id())).as(e.id()).isTrue();
      assertThat(VoiceLibrary.code(e.gender())).isIn("FEMALE", "MALE");
      assertThat(VoiceLibrary.code(e.ageGroup())).isIn("CHILD", "YOUNG", "MIDDLE", "OLD");
      if (e.dialect() != null) {
        assertThat(e.dialect()).isIn("GYEONGSANG", "JEOLLA", "CHUNGCHEONG", "GANGWON", "JEJU");
      }
    }
  }

  @Test
  @DisplayName("목소리 목록: 한국어·영어 검색 글이 모두 있고 컬럼 길이 안이다")
  void texts() {
    for (VoiceLibrary.Entry e : entries) {
      assertThat(e.searchText()).as(e.id()).isNotBlank();
      assertThat(e.searchTextEn()).as(e.id()).isNotBlank();
      assertThat(e.description()).as(e.id()).isNotBlank();
      assertThat(e.id()).hasSizeLessThanOrEqualTo(40);
      assertThat(e.name()).hasSizeLessThanOrEqualTo(20);
      assertThat(e.tone()).hasSizeLessThanOrEqualTo(60);
      assertThat(VoiceLibrary.join(e.traits())).hasSizeLessThanOrEqualTo(300);
      assertThat(VoiceLibrary.join(e.roles())).hasSizeLessThanOrEqualTo(500);
      assertThat(VoiceLibrary.join(e.concepts())).hasSizeLessThanOrEqualTo(200);
    }
  }

  @Test
  @DisplayName("쉼표 목록: 빈 값과 앞뒤 공백을 뺀다")
  void csv() {
    assertThat(VoiceLibrary.join(List.of(" 밝음", "", "맑음 "))).isEqualTo("밝음,맑음");
    assertThat(VoiceLibrary.join(null)).isEmpty();
    assertThat(VoiceLibrary.split("밝음, 맑음,")).containsExactly("밝음", "맑음");
    assertThat(VoiceLibrary.split(" ")).isEmpty();
  }
}
