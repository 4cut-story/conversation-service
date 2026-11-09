package net.java21.fourcut.conversation.sound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SoundMatcherTest {

  private static SoundLibrary.Sound sound(String id, String... places) {
    return described(id, "소리 " + id, "", places);
  }

  private static SoundLibrary.Sound described(
      String id, String title, String description, String... places) {
    return new SoundLibrary.Sound(
        id,
        "AMBIENCE",
        "CITY",
        title,
        description,
        List.of(),
        List.of(places),
        30000,
        new SoundLibrary.Credit(
            "freesound", "https://x/" + id, null, "작가", "CC-BY-4.0", "https://l"));
  }

  @Test
  @DisplayName("FR-008: 가까운 후보 중 장소 낱말이 소리 장면에 있는 소리를 먼저 고르고 저작자 표시를 함께 준다")
  void placeFirst() {
    SoundMatcher matcher =
        new SoundMatcher(
            (q, n) ->
                List.of(
                    sound("aaaaaaaaaaaaaaaaaaaaa", "카페"), sound("bbbbbbbbbbbbbbbbbbbbb", "골목")));

    var picked = matcher.match(List.of(new SoundMatcher.Query("비 오는 골목, 빗소리", "alley", "")));

    assertThat(picked.get(0)).isPresent();
    assertThat(picked.get(0).get().publicId()).isEqualTo("bbbbbbbbbbbbbbbbbbbbb");
    assertThat(picked.get(0).get().author()).isEqualTo("작가");
    assertThat(picked.get(0).get().license()).isEqualTo("CC-BY-4.0");
  }

  @Test
  @DisplayName("FR-008: 장소 낱말만 맞는 먼 후보는 가까운 후보를 앞지르지 못한다 (마트 장면의 와인병 소리)")
  void placeOnlyNearTop() {
    SoundMatcher matcher =
        new SoundMatcher(
            (q, n) ->
                List.of(
                    sound("aaaaaaaaaaaaaaaaaaaaa", "식당"),
                    sound("bbbbbbbbbbbbbbbbbbbbb", "클럽"),
                    sound("ccccccccccccccccccccc", "광장"),
                    sound("ddddddddddddddddddddd", "시장"),
                    sound("eeeeeeeeeeeeeeeeeeeee", "마트")));

    var picked =
        matcher.match(List.of(new SoundMatcher.Query("마트 시식 코너, 사람들 웅성임", "supermarket", "")));

    assertThat(picked.get(0)).map(SoundMatcher.Picked::publicId).contains("aaaaaaaaaaaaaaaaaaaaa");
  }

  @Test
  @DisplayName("FR-008: 시간대·날씨가 장면과 어긋나는 소리는 뒤로 보낸다")
  void timeAndWeather() {
    var night = described("aaaaaaaaaaaaaaaaaaaaa", "밤 풀벌레", "한밤 공원에서 귀뚜라미가 울어요");
    var rain = described("bbbbbbbbbbbbbbbbbbbbb", "창밖 빗소리", "비가 창문을 두드려요");
    var room = described("ccccccccccccccccccccc", "조용한 방", "시계 소리가 작게 들려요");
    SoundMatcher matcher = new SoundMatcher((q, n) -> List.of(night, rain, room));

    var picked =
        matcher.match(
            List.of(
                new SoundMatcher.Query("아침 방 안, 시계 소리", "bedroom", ""),
                new SoundMatcher.Query("한밤 공원, 풀벌레", "park", ""),
                new SoundMatcher.Query("아침 방 안, 창밖 빗소리", "bedroom", "")));

    assertThat(picked)
        .extracting(p -> p.get().publicId())
        .containsExactly("ccccccccccccccccccccc", "aaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbb");
    assertThat(SoundMatcher.conflicts("morning", night)).isTrue();
    assertThat(SoundMatcher.conflicts("night", described("x", "아침 새소리", ""))).isTrue();
    assertThat(SoundMatcher.conflicts("", night)).isFalse();
  }

  @Test
  @DisplayName("FR-009: 앞 컷과 같은 장소이고 앞 소리가 후보 위쪽에 있으면 같은 소리를 이어 쓴다")
  void keepsPrevious() {
    Map<String, List<SoundLibrary.Sound>> byQuery =
        Map.of(
            "first", List.of(sound("aaaaaaaaaaaaaaaaaaaaa")),
            "second", List.of(sound("bbbbbbbbbbbbbbbbbbbbb"), sound("aaaaaaaaaaaaaaaaaaaaa")),
            "third", List.of(sound("bbbbbbbbbbbbbbbbbbbbb"), sound("aaaaaaaaaaaaaaaaaaaaa")));
    SoundMatcher matcher =
        new SoundMatcher((q, n) -> byQuery.getOrDefault(q.split("\\.")[0], List.of()));

    var picked =
        matcher.match(
            List.of(
                new SoundMatcher.Query("first", "cafe", null),
                new SoundMatcher.Query("second", "cafe", null),
                new SoundMatcher.Query("third", "park", null)));

    assertThat(picked)
        .extracting(p -> p.get().publicId())
        .containsExactly("aaaaaaaaaaaaaaaaaaaaa", "aaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbb");
  }

  @Test
  @DisplayName("FR-007: 긴 질의로 못 찾으면 소리 장면 낱말 하나씩 찾고, 그래도 없거나 오류면 소리 없음이다")
  void fallbackAndEmpty() {
    List<String> asked = new ArrayList<>();
    SoundMatcher words =
        new SoundMatcher(
            (q, n) -> {
              asked.add(q);
              return q.equals("빗소리") ? List.of(sound("ccccccccccccccccccccc")) : List.of();
            });
    assertThat(words.match(List.of(new SoundMatcher.Query("골목 빗소리", null, "가자"))).get(0))
        .map(SoundMatcher.Picked::publicId)
        .contains("ccccccccccccccccccccc");
    assertThat(asked).containsExactly("골목 빗소리", "골목", "빗소리");

    SoundMatcher broken =
        new SoundMatcher(
            (q, n) -> {
              throw new IllegalStateException("down");
            });
    assertThat(broken.match(List.of(new SoundMatcher.Query("비", null, null))))
        .containsExactly(Optional.empty());
    assertThat(
            new SoundMatcher((q, n) -> List.of())
                .match(List.of(new SoundMatcher.Query(null, null, null))))
        .containsExactly(Optional.empty());
  }

  @Test
  @DisplayName("FR-019: 사건 소리가 있는 컷만 효과음에서 고르고, 긴 말로 못 찾으면 낱말로 찾는다")
  void effects() {
    List<String> asked = new ArrayList<>();
    SoundMatcher matcher =
        new SoundMatcher(
            (q, n) -> List.of(),
            (q, n) -> {
              asked.add(q);
              return q.equals("알람") ? List.of(sound("ddddddddddddddddddddd")) : List.of();
            });

    var picked = matcher.effects(java.util.Arrays.asList("휴대폰 알람", "", null));

    assertThat(picked.get(0)).map(SoundMatcher.Picked::publicId).contains("ddddddddddddddddddddd");
    assertThat(picked.get(1)).isEmpty();
    assertThat(picked.get(2)).isEmpty();
    assertThat(asked).containsExactly("휴대폰 알람", "휴대폰", "알람");

    SoundMatcher broken =
        new SoundMatcher(
            (q, n) -> List.of(),
            (q, n) -> {
              throw new IllegalStateException("down");
            });
    assertThat(broken.effects(List.of("알람"))).containsExactly(Optional.empty());
    assertThat(new SoundMatcher((q, n) -> List.of()).effects(List.of("알람")))
        .containsExactly(Optional.empty());
  }
}
