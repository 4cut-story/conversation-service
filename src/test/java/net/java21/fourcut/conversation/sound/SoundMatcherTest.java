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
  @DisplayName("FR-008: 시간대·날씨가 장면과 어긋나는 소리는 쓰지 않는다")
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
        new SoundMatcher(
            (q, n) -> byQuery.getOrDefault(q.split("\\.")[0].replace("들리는 소리: ", ""), List.of()));

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
    assertThat(asked).containsExactly("들리는 소리: 골목 빗소리.", "골목", "빗소리");

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
  @DisplayName("FR-020: 사건 소리가 있는 컷만 효과음에서 고르고, 긴 말로 못 찾으면 낱말로 찾는다")
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

  @Test
  @DisplayName("FR-021: 효과음은 사건 소리의 끝 낱말(중심)이 제목에 있는 후보를 먼저 고른다 (휴대폰 알람 → 진동이 아니라 알람)")
  void effectHead() {
    var vibrate = described("aaaaaaaaaaaaaaaaaaaaa", "휴대폰 진동", "");
    var alarm = described("bbbbbbbbbbbbbbbbbbbbb", "전자 알람시계", "");
    SoundMatcher matcher = new SoundMatcher((q, n) -> List.of(), (q, n) -> List.of(vibrate, alarm));

    assertThat(matcher.effects(List.of("휴대폰 알람 소리")).get(0))
        .map(SoundMatcher.Picked::publicId)
        .contains("bbbbbbbbbbbbbbbbbbbbb");
    assertThat(SoundMatcher.headFirst("초인종", List.of(vibrate, alarm))).isSameAs(vibrate);
  }

  @Test
  @DisplayName("FR-008: 거리가 사실상 같은 배경음 후보는 만화(seed)마다 다르게 고르고, 먼 후보는 고르지 않는다")
  void tiedPicksVary() {
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) ->
                List.of(
                    new SoundMatcher.Scored(sound("aaaaaaaaaaaaaaaaaaaaa"), 0.10),
                    new SoundMatcher.Scored(sound("bbbbbbbbbbbbbbbbbbbbb"), 0.11),
                    new SoundMatcher.Scored(sound("ccccccccccccccccccccc"), 0.30)));
    var query = List.of(new SoundMatcher.Query("조용한 방, 시계 소리", "room", ""));

    var seen = new java.util.HashSet<String>();
    for (long seed = 0; seed < 6; seed++) {
      seen.add(matcher.match(query, seed).get(0).orElseThrow().publicId());
    }

    assertThat(seen).containsExactlyInAnyOrder("aaaaaaaaaaaaaaaaaaaaa", "bbbbbbbbbbbbbbbbbbbbb");
    assertThat(matcher.match(query).get(0).orElseThrow().publicId())
        .isEqualTo(matcher.match(query).get(0).orElseThrow().publicId());
  }

  private static SoundLibrary.Sound music(String id) {
    return new SoundLibrary.Sound(
        id, "MUSIC", "잔잔한", "잔잔한 피아노", "", List.of(), List.of(), 60_000, null);
  }

  @Test
  @DisplayName("FR-008b: 거리 한도(0.40)보다 먼 장소 소리는 쓰지 않고, 바탕 배경음악을 작게 깐다")
  void farFallsBackToMusic() {
    var near = sound("aaaaaaaaaaaaaaaaaaaaa");
    var far = sound("bbbbbbbbbbbbbbbbbbbbb");
    var piano = music("mmmmmmmmmmmmmmmmmmmmm");
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) ->
                q.contains("날씨: 비")
                    ? List.of(new SoundMatcher.Scored(near, 0.25))
                    : List.of(new SoundMatcher.Scored(far, 0.55)),
            (q, n) -> List.of(),
            (q, n) -> List.of(new SoundMatcher.Scored(piano, 0.7)));

    var picked =
        matcher.match(
            List.of(
                new SoundMatcher.Query("비 오는 골목, 빗소리", "alley", ""),
                new SoundMatcher.Query("마트 계산대", "mart", ""),
                new SoundMatcher.Query("마트 진열대", "mart", "")));

    assertThat(picked.get(0).orElseThrow().publicId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaa");
    assertThat(picked.get(0).orElseThrow().score()).isEqualTo(0.75);
    assertThat(picked.get(0).orElseThrow().music()).isFalse();
    assertThat(picked.get(1).orElseThrow().publicId()).isEqualTo("mmmmmmmmmmmmmmmmmmmmm");
    assertThat(picked.get(1).orElseThrow().music()).isTrue();
    assertThat(picked.get(2).orElseThrow().publicId()).isEqualTo("mmmmmmmmmmmmmmmmmmmmm");
  }

  @Test
  @DisplayName("FR-020: 거리 한도보다 먼 효과음은 넣지 않는다")
  void farEffectSkipped() {
    var alarm = described("ddddddddddddddddddddd", "알람시계", "아침 알람시계가 울려요");
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) -> List.of(),
            (q, n) ->
                q.contains("알람")
                    ? List.of(new SoundMatcher.Scored(alarm, 0.2))
                    : List.of(new SoundMatcher.Scored(alarm, 0.6)));

    var picked = matcher.effects(List.of("알람시계가 울린다", "냄비가 끓는다", ""));

    assertThat(picked.get(0).orElseThrow().publicId()).isEqualTo("ddddddddddddddddddddd");
    assertThat(picked.get(1)).isEmpty();
    assertThat(picked.get(2)).isEmpty();
    // 장소 소리도 음악도 없으면 소리 없음
    assertThat(matcher.match(List.of(new SoundMatcher.Query("마트", "mart", ""))))
        .containsExactly(Optional.empty());
  }

  @Test
  @DisplayName("FR-008c: 유사도 0.70보다 먼 장소 소리는 장면 문장이 그 장소·소리를 말해도 쓰지 않고 배경음악을 깐다 (공용실 형광등 웅웅에 환풍구)")
  void graySoundNotUsed() {
    var hum = described("hhhhhhhhhhhhhhhhhhhhh", "기계실 웅웅거림", "", "기계실", "지하");
    var diner = described("ddddddddddddddddddddd", "식당 손님들 말소리", "", "식당");
    var piano = music("mmmmmmmmmmmmmmmmmmmmm");
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) ->
                List.of(new SoundMatcher.Scored(hum, 0.33), new SoundMatcher.Scored(diner, 0.36)),
            (q, n) -> List.of(),
            (q, n) -> List.of(new SoundMatcher.Scored(piano, 0.5)));

    for (String scene :
        List.of("낮 햄버거 가게 안, 손님들이 웅성이는 소리", "저녁 식당 안, 손님들 말소리", "밤 지하 기계실, 낮게 웅웅거림")) {
      var picked = matcher.match(List.of(new SoundMatcher.Query(scene, "room", "")));
      assertThat(picked.get(0).orElseThrow().publicId()).isEqualTo("mmmmmmmmmmmmmmmmmmmmm");
    }
  }

  @Test
  @DisplayName("FR-008d: 맞는 장소 소리가 없는 컷에는 만화 분위기의 배경음악 한 곡을 이어 깐다")
  void storyMoodMusic() {
    var vent = described("vvvvvvvvvvvvvvvvvvvvv", "환풍구 웅웅", "", "사무실", "복도");
    var sad = music("sssssssssssssssssssss");
    var sad2 = music("ttttttttttttttttttttt");
    List<String> asked = new ArrayList<>();
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) -> List.of(new SoundMatcher.Scored(vent, 0.38)),
            (mood, q, n) -> {
              asked.add(mood + "|" + q);
              return "SAD".equals(mood)
                  ? List.of(new SoundMatcher.Scored(sad, 0.30), new SoundMatcher.Scored(sad2, 0.45))
                  : List.of();
            },
            t -> Optional.of("SOLEMN"));
    List<SoundMatcher.Query> panels =
        List.of(
            new SoundMatcher.Query("저녁 빌라 공용실 안, 형광등이 낮게 웅웅거리는 소리", "community room", ""),
            new SoundMatcher.Query("저녁 빌라 공용실 안, 형광등이 낮게 웅웅거리는 소리", "community room", ""));

    var picked = matcher.match(panels, 7, new SoundMatcher.Story("SAD", "세입자가 억울함을 털어놓는다."));

    assertThat(picked)
        .allSatisfy(p -> assertThat(p.orElseThrow().publicId()).isEqualTo("sssssssssssssssssssss"));
    assertThat(asked).containsExactly("SAD|세입자가 억울함을 털어놓는다.");
  }

  @Test
  @DisplayName("FR-008d: 분위기를 적지 않은 예전 콘티는 줄거리로 분위기를 짐작한다")
  void guessedMood() {
    var calm = music("ccccccccccccccccccccc");
    List<String> moods = new ArrayList<>();
    SoundMatcher matcher =
        SoundMatcher.scored(
            (q, n) -> List.of(),
            (mood, q, n) -> {
              moods.add(mood);
              return List.of(new SoundMatcher.Scored(calm, 0.4));
            },
            t -> Optional.of("SAD"));

    var picked =
        matcher.match(
            List.of(new SoundMatcher.Query("저녁 방 안, 조용함", "room", "")),
            1,
            new SoundMatcher.Story(null, "힘든 일을 털어놓는다."));

    assertThat(picked.get(0).orElseThrow().publicId()).isEqualTo("ccccccccccccccccccccc");
    assertThat(moods).containsExactly("SAD");
  }

  @Test
  @DisplayName("FR-008c: 유사도 0.70 이상이면 장면 낱말이 없어도 쓰고, 소리 이름의 중심 낱말은 끝 낱말이다")
  void sureSoundKept() {
    var waves = described("wwwwwwwwwwwwwwwwwwwww", "밤바다에 밀려오는 잔잔한 파도 소리", "");
    SoundMatcher matcher =
        SoundMatcher.scored((q, n) -> List.of(new SoundMatcher.Scored(waves, 0.21)));

    assertThat(matcher.match(List.of(new SoundMatcher.Query("밤, 별이 가득한 하늘", "beach", ""))).get(0))
        .isPresent();
    assertThat(SoundMatcher.head("밤바다에 밀려오는 잔잔한 파도 소리")).isEqualTo("파도");
    assertThat(SoundMatcher.head("기계실 웅웅거림")).isEqualTo("웅웅거림");
  }

  @Test
  @DisplayName("FR-008e: 장소·환경음 질의는 소리 설명과 같은 항목 틀(들리는 소리·장소·시간대·날씨)로 쓴다")
  void soundQueryUsesDescriptionTemplate() {
    assertThat(
            SoundMatcher.soundQuery(
                new SoundMatcher.Query("저녁 술집 안, 사람들 웅성거림과 잔 부딪는 소리", "a pub", "건배!")))
        .isEqualTo("들리는 소리: 사람들 웅성거림과 잔 부딪는 소리. 장소: 술집 안. 시간대: 저녁.");
    assertThat(SoundMatcher.soundQuery(new SoundMatcher.Query("비 오는 골목, 지붕을 두드리는 빗소리", null, null)))
        .isEqualTo("들리는 소리: 지붕을 두드리는 빗소리. 장소: 골목. 날씨: 비.");
    assertThat(SoundMatcher.soundQuery(new SoundMatcher.Query("밤, 풀벌레 소리", "a park", null)))
        .isEqualTo("들리는 소리: 풀벌레 소리. 장소: a park. 시간대: 밤.");
    assertThat(SoundMatcher.soundQuery(new SoundMatcher.Query(null, "a cafe", "안녕")))
        .isEqualTo(SoundMatcher.query(new SoundMatcher.Query(null, "a cafe", "안녕")));
  }
}
