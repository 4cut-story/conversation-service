package net.java21.fourcut.conversation.scenario;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 라이브 모드의 장면 맞춤 배경 움직임 (docs specs/003-live-mode FR-049). 날씨·물·별·불이 없는 사무실·식당·집 컷은 모두 가벼운 바람(잎·풀·천
 * 흔들기)으로 그려져서, 여러 편을 이어 보면 같은 움직임만 되풀이됐다(최근 40편 중 움직임이 있는 컷의 52%, 그날 만든 9편은 모든 컷). 콘티가 컷마다 그 배경에
 * 실제로 있는 물건 하나가 어떻게 움직이는지 한 구절로 쓰고(예: 벽시계 바늘이 움직인다, 모니터 화면이 바뀐다, 냄비에서 김이 오른다), 움직임 그림 세 장을 그 움직임의
 * 처음·중간· 끝으로 그린다. 코드는 그 물건이 컷 배경(setting)에 쓰여 있는지, 인물을 움직이지 않는지 다시 본다.
 */
public final class Stir {

  private Stir() {}

  /** 너무 짧으면 무엇이 움직이는지 모르고, 너무 길면 여러 것을 바꾼다. */
  static final int MIN_WORDS = 4;

  static final int MAX_WORDS = 24;

  /** 인물을 움직이는 구절. 움직임 그림은 배경만 바꾼다(인물이 바뀌면 얼굴이 흔들려 보인다). */
  private static final Pattern PERSON =
      Pattern.compile(
          "\\{|\\b(he|she|his|her|him|they|their|person|people|man|woman|boy|girl|character|"
              + "eyes|mouth|hair|smile|smiles|nod|nods)\\b");

  /** 물건 이름이 아닌 흔한 낱말. 남은 낱말 가운데 하나는 컷 배경에 쓰여 있어야 한다. */
  private static final Set<String> COMMON =
      Set.of(
          "the",
          "a",
          "an",
          "and",
          "of",
          "on",
          "in",
          "into",
          "onto",
          "from",
          "with",
          "over",
          "under",
          "behind",
          "across",
          "along",
          "through",
          "past",
          "up",
          "down",
          "back",
          "out",
          "off",
          "to",
          "by",
          "at",
          "its",
          "it",
          "one",
          "two",
          "few",
          "some",
          "slowly",
          "gently",
          "slightly",
          "softly",
          "little",
          "small",
          "big",
          "bit",
          "more",
          "new",
          "next",
          "other",
          "another",
          "light",
          "lights",
          "moves",
          "move",
          "moving",
          "turns",
          "turn",
          "turning",
          "changes",
          "change",
          "changing",
          "rises",
          "rise",
          "rising",
          "drifts",
          "drift",
          "drifting",
          "sways",
          "sway",
          "swaying",
          "passes",
          "pass",
          "passing",
          "flickers",
          "flicker",
          "blinks",
          "blink",
          "spins",
          "spin",
          "ticks",
          "tick",
          "forward",
          "away",
          "background",
          "window",
          "outside",
          "inside",
          "side",
          "far",
          "near",
          "end");

  private static final Pattern WORD = Pattern.compile("[a-z]+");

  /** 콘티가 쓴 구절을 거른다: 배경에 쓰인 물건을 움직이고 인물을 건드리지 않는 4~24낱말 구절만 남긴다. */
  public static Optional<String> clean(String stir, String setting) {
    if (stir == null || setting == null || stir.isBlank()) {
      return Optional.empty();
    }
    String text = stir.strip().replaceAll("[.\\s]+$", "").replaceAll("\\s+", " ");
    String lower = text.toLowerCase(Locale.ROOT);
    int words = text.split(" ").length;
    if (words < MIN_WORDS || words > MAX_WORDS || PERSON.matcher(lower).find()) {
      return Optional.empty();
    }
    Set<String> place = words(setting);
    boolean grounded = words(lower).stream().anyMatch(place::contains);
    return grounded ? Optional.of(text) : Optional.empty();
  }

  /** 앞 컷과 같은 것을 움직이는지(물건 낱말이 겹치는지). 이웃한 컷은 다른 것을 움직여야 이어 볼 때 새롭다. */
  public static boolean same(String stir, String previous) {
    if (stir == null || previous == null) {
      return false;
    }
    Set<String> a = words(stir);
    return words(previous).stream().anyMatch(a::contains);
  }

  private static Set<String> words(String text) {
    var m = WORD.matcher(text.toLowerCase(Locale.ROOT));
    Set<String> out = new java.util.HashSet<>();
    while (m.find()) {
      String w = m.group();
      if (w.length() >= 3 && !COMMON.contains(w)) {
        out.add(w.endsWith("s") && w.length() > 3 ? w.substring(0, w.length() - 1) : w);
      }
    }
    return out;
  }

  /** 새로 그리지 말 것. 그림에 없는 것을 그리면 그림마다 자리가 달라 깜빡인다. */
  static final String ONLY_THAT =
      "Change only that one thing in the background. Do not add anything that is not already in"
          + " the picture: no flying or falling leaves, no new plants, no wind lines, streaks or"
          + " ribbons in the air, no new objects. Keep the colors and the lighting exactly the"
          + " same, and keep any rain streaks or snowflakes as they are";

  /** 두 번째 그림: 움직임의 처음. */
  public static String change(String stir) {
    return "show this small background motion just beginning: " + stir + ". " + ONLY_THAT;
  }

  /** 세 번째 그림: 움직임의 중간. */
  public static String next(String stir) {
    return "show this small background motion halfway along: " + stir + ". " + ONLY_THAT;
  }

  /** 네 번째 그림: 움직임이 더 나아간 모습. */
  public static String last(String stir) {
    return "show this small background motion further along than before: "
        + stir
        + ". "
        + ONLY_THAT;
  }
}
