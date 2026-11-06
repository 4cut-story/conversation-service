package net.java21.fourcut.conversation.character;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 외모 문구의 첫 마디("young woman", "boy in hanbok", "fox")에서 읽은 동물·성별·나이. 설명 임베딩은 머리·옷 색에 끌려 성별이나 나이가 다른
 * 캐릭터를 가깝게 내놓기 때문에, 가까운 후보 가운데 첫 마디가 어긋나지 않는 모습만 쓴다. 읽지 못한 값은 무엇과도 맞는다고 본다.
 *
 * @param animal 동물 컨셉의 동물 (없으면 null)
 * @param gender F, M (모르면 null)
 * @param age CHILD, YOUNG, MIDDLE, OLD, ADULT(어른인 것만 앎) (모르면 null)
 */
record LookHead(String animal, String gender, String age) {

  private static final Set<String> ANIMALS =
      Set.of("rabbit", "bear", "fox", "panda", "cat", "deer", "dog", "squirrel");
  private static final Map<String, String> ANIMAL_KO =
      Map.of(
          "토끼",
          "rabbit",
          "곰",
          "bear",
          "여우",
          "fox",
          "판다",
          "panda",
          "고양이",
          "cat",
          "사슴",
          "deer",
          "강아지",
          "dog",
          "개",
          "dog",
          "다람쥐",
          "squirrel");
  private static final Set<String> FEMALE =
      Set.of(
          "woman",
          "women",
          "girl",
          "lady",
          "swordswoman",
          "grandma",
          "grandmother",
          "mother",
          "queen",
          "princess",
          "여자",
          "여자아이",
          "할머니",
          "소녀",
          "엄마",
          "여검객");
  private static final Set<String> MALE =
      Set.of(
          "man",
          "men",
          "boy",
          "gentleman",
          "swordsman",
          "grandpa",
          "grandfather",
          "father",
          "king",
          "prince",
          "남자",
          "남자아이",
          "할아버지",
          "소년",
          "아빠",
          "검객");
  private static final Set<String> CHILD =
      Set.of("little", "girl", "boy", "kid", "child", "어린", "아이", "여자아이", "남자아이", "소녀", "소년");
  private static final Set<String> OLD =
      Set.of("old", "elderly", "grandma", "grandpa", "grandmother", "grandfather", "할머니", "할아버지");
  private static final Set<String> MIDDLE = Set.of("middle-aged", "중년");
  private static final Set<String> YOUNG = Set.of("young", "teen", "teenage", "젊은");
  private static final Set<String> ADULT =
      Set.of("man", "men", "woman", "women", "lady", "gentleman", "남자", "여자");

  static LookHead of(String look) {
    if (look == null) {
      return new LookHead(null, null, null);
    }
    String head = look.split(",", 2)[0].toLowerCase(Locale.ROOT);
    Set<String> t =
        Arrays.stream(head.split("[^\\p{L}\\p{N}-]+"))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    String animal =
        t.stream()
            .map(s -> ANIMALS.contains(s) ? s : ANIMAL_KO.get(s))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    String age = age(t);
    if (age == null || "ADULT".equals(age)) {
      // 첫 마디에 나이가 없어도("woman, gray hair bun") 뒤쪽에 노인 표시가 있으면 노인으로 본다
      Set<String> all =
          Arrays.stream(look.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}-]+"))
              .filter(x -> !x.isEmpty())
              .collect(Collectors.toSet());
      if (any(all, OLD) || any(all, WRINKLES) || (any(all, GRAY) && all.contains("hair"))) {
        age = "OLD";
      }
    }
    return new LookHead(animal, gender(t), age);
  }

  private static final Set<String> WRINKLES = Set.of("wrinkles", "wrinkled", "wrinkly");
  private static final Set<String> GRAY = Set.of("gray", "grey", "silver", "white");

  private static String gender(Set<String> t) {
    if (any(t, FEMALE)) {
      return "F";
    }
    return any(t, MALE) ? "M" : null;
  }

  private static String age(Set<String> t) {
    if (any(t, CHILD)) {
      return "CHILD";
    }
    if (any(t, OLD)) {
      return "OLD";
    }
    if (any(t, MIDDLE)) {
      return "MIDDLE";
    }
    if (any(t, YOUNG)) {
      return "YOUNG";
    }
    return any(t, ADULT) ? "ADULT" : null;
  }

  private static boolean any(Set<String> tokens, Set<String> words) {
    return tokens.stream().anyMatch(words::contains);
  }

  /** 이 첫 마디로 찾을 때 그 모습을 써도 되는지. */
  boolean accepts(LookHead other) {
    if (animal != null || other.animal != null) {
      return animal != null && animal.equals(other.animal);
    }
    return (gender == null || other.gender == null || gender.equals(other.gender))
        && ages(age, other.age);
  }

  private static boolean ages(String a, String b) {
    if (a == null || b == null || a.equals(b)) {
      return true;
    }
    if ("ADULT".equals(a) || "ADULT".equals(b)) {
      return !"CHILD".equals(a) && !"CHILD".equals(b);
    }
    return false;
  }
}
