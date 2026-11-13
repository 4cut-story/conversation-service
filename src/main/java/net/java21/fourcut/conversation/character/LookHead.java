package net.java21.fourcut.conversation.character;

import java.util.Arrays;
import java.util.List;
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
public record LookHead(String animal, String gender, String age) {

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

  /**
   * 인원 세기에 쓰는 사람 말("young man", "old woman", "little girl"). 첫 마디가 "man in hanbok", "young man with
   * glasses"처럼 길면 "two people"로만 셌고, 그림 모델은 남녀 한 쌍이나 같은 나이로 그렸다(시험 8회 우산 만화 두 남자가 남녀로, 고추 자랑 만화 손주가
   * 노인으로). 동물이거나 성별을 모르면 null.
   */
  static String personWord(String look) {
    LookHead h = of(look);
    if (h.animal() != null || h.gender() == null) {
      return null;
    }
    boolean f = "F".equals(h.gender());
    String head = look.split(",", 2)[0].toLowerCase(Locale.ROOT);
    if ("CHILD".equals(h.age())) {
      return (head.contains("little") ? "little " : "") + (f ? "girl" : "boy");
    }
    String noun = f ? "woman" : "man";
    if (h.age() == null || "ADULT".equals(h.age())) {
      return head.startsWith("tall adult") ? "tall adult " + noun : noun;
    }
    return switch (h.age()) {
      case "OLD" -> "old " + noun;
      case "MIDDLE" -> "middle-aged " + noun;
      default -> "young " + noun;
    };
  }

  public static LookHead of(String look) {
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

  /**
   * 모습 문구의 아이·어른이 캐릭터 DB 나이대와 맞는지. 그림책 모습은 DB 나이와 상관없이 모두 "little girl/boy"로 만들어져, 어른 캐릭터의 아이 모습이
   * 엄마·아빠 자리에 붙어 엄마·아빠가 아이로 그려졌고(시험 8회 지우개 만화), 아이 자리(딸)에는 어른 캐릭터가 붙었다. 한쪽이라도 모르면 맞는 것으로 본다.
   *
   * @param dbAge CHILD, YOUNG, MIDDLE, OLD (없으면 null)
   */
  static boolean agesWithDb(String look, String dbAge) {
    String age = of(look).age();
    if (age == null || dbAge == null) {
      return true;
    }
    return "CHILD".equals(age) == "CHILD".equals(dbAge);
  }

  /**
   * 캐릭터 DB의 성별·나이대로 비어 있는 값을 채운다. DB 모습 문구가 "blonde hair, blue overalls"처럼 성별·나이 없이 시작하면 무엇과도 맞아서
   * 할아버지 자리에 금발 소녀가 붙었다.
   *
   * @param dbGender FEMALE, MALE (없으면 null)
   * @param dbAge CHILD, YOUNG, MIDDLE, OLD (없으면 null)
   */
  LookHead orDb(String dbGender, String dbAge) {
    String g = gender;
    if (g == null && dbGender != null) {
      g =
          switch (dbGender) {
            case "FEMALE" -> "F";
            case "MALE" -> "M";
            default -> null;
          };
    }
    String a = age;
    if ((a == null || "ADULT".equals(a)) && dbAge != null) {
      a = dbAge;
    }
    return new LookHead(animal, g, a);
  }

  /**
   * 성별 없이 시작하는 모습 문구 앞에 캐릭터 DB 성별·나이대 명사("boy", "old woman")를 붙인다. 동물이거나 이미 성별이 있거나 DB 성별을 모르면 그대로
   * 둔다. "curly brown hair, white hoodie"인 오빠가 그림책 컷에서 양 갈래 머리 여자아이로 그려졌다.
   */
  static String withNoun(String look, String dbGender, String dbAge) {
    String l = look == null ? "" : look.strip();
    LookHead h = of(l);
    if (h.animal != null || h.gender != null || dbGender == null) {
      return l;
    }
    boolean f = "FEMALE".equals(dbGender);
    if (!f && !"MALE".equals(dbGender)) {
      return l;
    }
    String age = dbAge == null ? "" : dbAge;
    String noun =
        switch (age) {
          case "CHILD" -> f ? "girl" : "boy";
          case "YOUNG" -> f ? "young woman" : "young man";
          case "MIDDLE" -> f ? "middle-aged woman" : "middle-aged man";
          case "OLD" -> f ? "old woman" : "old man";
          default -> f ? "woman" : "man";
        };
    return l.isEmpty() ? noun : noun + ", " + l;
  }

  /**
   * 첫 마디에 나이가 없으면("man in hanbok", "swordsman", "bear") 캐릭터 DB 나이대를 넣는다. 그림체별 모습을 만들 때 나이가 빠져 조선·무협
   * 노인과 무협 아이가 젊은 어른으로, 동물 컨셉 할머니가 아기 동물처럼 그려졌다. 첫 마디가 이미 나이를 말하면 참조 그림도 그 나이라 그대로 둔다.
   */
  static String withAge(String look, String dbGender, String dbAge) {
    String l = withNoun(look, dbGender, dbAge);
    LookHead h = of(l);
    if (dbAge == null || (h.age != null && !"ADULT".equals(h.age))) {
      return l;
    }
    String[] parts = l.split(",", 2);
    String head = parts[0].strip();
    String rest = parts.length > 1 ? "," + parts[1] : "";
    String lower = head.toLowerCase(Locale.ROOT);
    String out;
    if (h.animal != null) {
      out =
          switch (dbAge) {
            case "OLD" -> "old " + head;
            case "CHILD" -> "little " + head;
            default -> head;
          };
    } else if (lower.startsWith("swordsman") || lower.startsWith("swordswoman")) {
      out = adjective(dbAge) + head;
    } else {
      String g = h.gender != null ? h.gender : "FEMALE".equals(dbGender) ? "F" : null;
      if (g == null && !"MALE".equals(dbGender)) {
        return l;
      }
      boolean f = "F".equals(g);
      int in = lower.indexOf(" in ");
      out = noun(f, dbAge) + (in >= 0 ? head.substring(in) : "");
    }
    return out + rest;
  }

  private static String adjective(String age) {
    return switch (age) {
      case "CHILD" -> "little ";
      case "YOUNG" -> "young ";
      case "MIDDLE" -> "middle-aged ";
      case "OLD" -> "old ";
      default -> "";
    };
  }

  private static String noun(boolean f, String age) {
    return switch (age) {
      case "CHILD" -> f ? "girl" : "boy";
      case "YOUNG" -> f ? "young woman" : "young man";
      case "MIDDLE" -> f ? "middle-aged woman" : "middle-aged man";
      case "OLD" -> f ? "old woman" : "old man";
      default -> f ? "woman" : "man";
    };
  }

  /** 직업 옷·소품. 콘티가 말하지 않았는데 붙으면 장면과 어긋난다. */
  private static final List<String> COSTUMES =
      List.of(
          "doctor coat",
          "firefighter coat",
          "fire helmet",
          "trench coat",
          "fedora",
          "police uniform",
          "police cap",
          "postal uniform",
          "waitress uniform",
          "chef jacket",
          "chef hat",
          "work coverall",
          "space suit",
          "space helmet",
          "florist apron",
          "painter apron",
          "barista apron",
          "shop apron",
          "sports jersey",
          "armor",
          "monk robe",
          "doublet",
          "medieval gown",
          "hooded cloak");

  /**
   * 직업 옷 이름 중 흔한 옷 낱말. 콘티의 "red cap", "green apron"이 "police cap", "painter apron"을 받아 경찰 모자를 쓴 아들,
   * 화가 앞치마를 두른 할머니가 그려졌다(회차 10). 직업을 가리는 낱말(police, painter 등)이 콘티에 있을 때만 받는다.
   */
  private static final Set<String> GENERIC_WEAR =
      Set.of("coat", "cap", "hat", "apron", "uniform", "jacket", "helmet", "suit", "robe", "gown");

  /**
   * 캐릭터 모습이 콘티 인물에 어울리는지. 캐릭터 DB 일상 모습의 절반(1,013명 중 480명)이 경찰복·의사 가운·우주복 같은 직업 옷이다. 콘티가 그 옷을 말하지
   * 않았으면 그 모습은 쓰지 않는다. 집에서 저녁 먹는 아빠가 우주복을, 할머니 집 손녀가 우체부 옷을 입고 나왔다. 콘티가 같은 낱말(uniform, apron, coat
   * 등)을 쓰면 받는다.
   */
  static boolean fitsOutfit(String wantedLook, String characterLook) {
    if (characterLook == null) {
      return true;
    }
    String c = characterLook.toLowerCase(Locale.ROOT);
    String w = wantedLook == null ? "" : wantedLook.toLowerCase(Locale.ROOT);
    Set<String> wanted =
        Arrays.stream(w.split("[^\\p{L}\\p{N}-]+"))
            .filter(x -> !x.isEmpty())
            .collect(Collectors.toSet());
    for (String costume : COSTUMES) {
      if (c.contains(costume)
          && Arrays.stream(costume.split(" "))
              .filter(word -> !GENERIC_WEAR.contains(word))
              .noneMatch(wanted::contains)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 캐릭터 모습의 머리 길이가 성별과 어울리는지. 일상 캐릭터 참조 그림은 성별과 상관없이 머리 모양을 골라 그려서, 긴 머리 남자(long black hair)는 여자처럼,
   * 짧은 머리 여자(short brown hair)는 남자처럼 보인다. 그림 모델은 참조 그림을 따라 그려서 콘티의 남자 안내원이 여자로 그려지고, 목소리는 콘티 성별대로
   * 남자 목소리가 나왔다. 콘티가 같은 머리 길이를 말했을 때만 받는다.
   *
   * @param wantedLook 콘티 외모 문구
   * @param characterLook 캐릭터 DB 모습 문구
   * @param gender 캐릭터 성별 F, M (모르면 null)
   */
  static boolean fitsHair(String wantedLook, String characterLook, String gender) {
    if (characterLook == null || gender == null) {
      return true;
    }
    String length = "M".equals(gender) ? "long" : "F".equals(gender) ? "short" : null;
    if (length == null || !hasHairLength(characterLook, length)) {
      return true;
    }
    return hasHairLength(wantedLook, length);
  }

  /** 외모 문구의 한 마디에 머리 길이 낱말과 hair가 함께 있는지("long black hair"). */
  private static boolean hasHairLength(String look, String length) {
    if (look == null) {
      return false;
    }
    for (String part : look.toLowerCase(Locale.ROOT).split(",")) {
      List<String> words = Arrays.asList(part.strip().split("[^\\p{L}\\p{N}-]+"));
      if (words.contains(length) && words.contains("hair")) {
        return true;
      }
    }
    return false;
  }

  /** 머리·얼굴·옷을 말하는 낱말. 이런 마디는 캐릭터 모습(참조 그림)을 따른다. */
  private static final Set<String> APPEARANCE =
      Set.of(
          "hair",
          "bob",
          "pigtails",
          "pigtail",
          "twintails",
          "bangs",
          "afro",
          "mohawk",
          "updo",
          "goatee",
          "freckles",
          "buzz",
          "shaved",
          "ponytail",
          "bun",
          "buns",
          "perm",
          "braid",
          "braided",
          "braids",
          "beard",
          "mustache",
          "glasses",
          "topknot",
          "gat",
          "bald",
          "curly",
          "shirt",
          "t-shirt",
          "hoodie",
          "coat",
          "raincoat",
          "jacket",
          "apron",
          "sweater",
          "cardigan",
          "dress",
          "robe",
          "jeogori",
          "chima",
          "hanbok",
          "hat",
          "cap",
          "scarf",
          "uniform",
          "overalls",
          "vest",
          "suit",
          "blouse",
          "skirt",
          "jeans",
          "pants",
          "pajamas",
          "headband",
          "beanie",
          "sweatshirt",
          "gown",
          "kimono",
          "hood",
          "sneakers",
          "boots",
          "tie",
          "ribbon",
          "collar",
          "polo",
          "parka",
          "cloak",
          "sash");

  /** 윗옷 낱말. */
  private static final Set<String> TOP =
      Set.of(
          "shirt",
          "t-shirt",
          "sweater",
          "hoodie",
          "jacket",
          "coat",
          "cardigan",
          "robe",
          "jeogori",
          "blouse",
          "vest",
          "dopo",
          "sweatshirt",
          "polo",
          "parka",
          "uniform",
          "apron",
          "top");

  /** 아래옷 낱말. */
  private static final Set<String> BOTTOM =
      Set.of("pants", "jeans", "skirt", "chima", "trousers", "shorts", "leggings");

  /** 아래까지 덮는 한 벌 옷. 이런 옷이 있으면 콘티의 아래옷을 붙이지 않는다(멜빵바지 위에 청바지). */
  private static final Set<String> ONE_PIECE =
      Set.of("overalls", "dress", "gown", "jumpsuit", "kimono", "pajamas");

  /** 신발 낱말. */
  private static final Set<String> SHOES =
      Set.of(
          "sneakers",
          "boots",
          "shoes",
          "sandals",
          "slippers",
          "flip-flops",
          "loafers",
          "heels",
          "barefoot",
          "feet");

  /**
   * 콘티 외모에서 머리·옷이 아닌 마디(소품: "brass telescope", "wooden sword"). 첫 마디(나이·성별)는 빼고, 캐릭터 모습에 이미 있는 마디도
   * 뺀다. 아래옷("blue jeans") 마디는 캐릭터 모습에 아래옷이 없을 때만 붙이고, 신발 마디는 캐릭터 모습이 없을 때만 붙인다. 모습 문구에 아래옷·신발이 없으면
   * 컷마다 바지 색이 바뀌었다(회차 29). 없으면 빈 문자열.
   */
  static String props(String scriptLook, String characterLook) {
    return props(scriptLook, characterLook, true);
  }

  /**
   * {@link #props(String, String)}와 같되, 참조 그림이 없는 인물({@code hasImage} false)은 캐릭터 모습에 없는 신발을 콘티에서
   * 붙인다. 캐릭터 모습에 윗옷이 없으면 콘티의 윗옷 마디도 붙인다. 윗옷이 없는 모습("old man, white beard, straw hat")은 컷마다 윗옷이
   * 바뀌었다(회차 29 콘티 7판 family-09).
   */
  static String props(String scriptLook, String characterLook, boolean hasImage) {
    if (scriptLook == null) {
      return "";
    }
    String c = characterLook == null ? "" : characterLook.toLowerCase(Locale.ROOT);
    Set<String> own = Set.copyOf(Arrays.asList(words(c)));
    boolean hasBottom = own.stream().anyMatch(w -> BOTTOM.contains(w) || ONE_PIECE.contains(w));
    // 참조 그림이 있는 인물은 신발을 그림이 정하게 둔다. 콘티 신발 글과 참조 그림 신발이 달라 컷마다 신발이 바뀌었다(회차 29 콘티 5판).
    boolean hasShoes = (hasImage && !c.isBlank()) || own.stream().anyMatch(SHOES::contains);
    boolean hasTop = own.stream().anyMatch(w -> TOP.contains(w) || ONE_PIECE.contains(w));
    String[] parts = scriptLook.split(",");
    return Arrays.stream(parts, 1, parts.length)
        .map(String::strip)
        .filter(p -> !p.isEmpty() && !c.contains(p.toLowerCase(Locale.ROOT)))
        .filter(p -> fits(words(p.toLowerCase(Locale.ROOT)), hasBottom, hasShoes, hasTop))
        // 성별·나이 마디("little girl")는 붙이지 않는다. 캐릭터 모습과 성별이 다른 콘티 마디가 붙어 한 컷에 남녀 아이가 둘
        // 그려졌다(회차 29 콘티 14판 mystery-08).
        .filter(
            p -> Arrays.stream(words(p.toLowerCase(Locale.ROOT))).noneMatch(PERSON_WORDS::contains))
        .collect(Collectors.joining(", "));
  }

  /**
   * 구도에 보이지 않는 마디를 뺀 외모. medium(허리 위)은 신발 마디를, close-up(가슴 위)은 아래옷 마디도 뺀다. 외모 글에 신발·바지가 있으면
   * close-up을 시켜도 온몸이 그려졌다(회차 29 구조화 콘티 판). 첫 마디(나이·성별)는 늘 남긴다.
   */
  public static String framed(String look, String shot) {
    if (look == null || !("medium".equals(shot) || "close-up".equals(shot))) {
      return look;
    }
    boolean close = "close-up".equals(shot);
    String[] parts = look.split(",");
    StringBuilder out = new StringBuilder(parts[0].strip());
    for (int i = 1; i < parts.length; i++) {
      String p = parts[i].strip();
      List<String> w = Arrays.asList(words(p.toLowerCase(Locale.ROOT)));
      boolean shoes = w.stream().anyMatch(SHOES::contains);
      boolean bottom = w.stream().anyMatch(BOTTOM::contains);
      if (!p.isEmpty() && !shoes && !(close && bottom)) {
        out.append(", ").append(p);
      }
    }
    return out.toString();
  }

  private static final Set<String> PERSON_WORDS =
      java.util.stream.Stream.of(FEMALE, MALE, Set.of("child", "kid", "children"))
          .flatMap(Set::stream)
          .collect(Collectors.toUnmodifiableSet());

  private static String[] words(String text) {
    return text.split("[^\\p{L}\\p{N}-]+");
  }

  /** 머리·옷 낱말이 없거나, 캐릭터 모습에 없는 아래옷·신발만 말하는 마디. */
  private static boolean fits(String[] words, boolean hasBottom, boolean hasShoes, boolean hasTop) {
    if (!hasTop
        && Arrays.stream(words).anyMatch(TOP::contains)
        && Arrays.stream(words).allMatch(w -> TOP.contains(w) || !APPEARANCE.contains(w))) {
      return true;
    }
    boolean bottom = Arrays.stream(words).anyMatch(BOTTOM::contains);
    boolean shoes = Arrays.stream(words).anyMatch(SHOES::contains);
    boolean other =
        Arrays.stream(words)
            .anyMatch(w -> APPEARANCE.contains(w) && !BOTTOM.contains(w) && !SHOES.contains(w));
    if (other || (bottom && shoes)) {
      return false;
    }
    if (bottom) {
      return !hasBottom;
    }
    return !shoes || !hasShoes;
  }

  /** 이 첫 마디로 찾을 때 그 모습을 써도 되는지. */
  boolean accepts(LookHead other) {
    if (animal != null || other.animal != null) {
      return animal != null && animal.equals(other.animal);
    }
    return (gender == null || other.gender == null || gender.equals(other.gender))
        && ages(age, other.age);
  }

  /**
   * {@link #accepts}에 더해, 이쪽 성별을 알 때 성별을 모르는 모습은 받지 않는다. 성별 없는 여자 모습이 아빠에게 붙어 참조 그림(긴 머리 여자)과
   * 프롬프트(남자)가 어긋났다(mystery-01).
   */
  boolean acceptsKnown(LookHead other) {
    return accepts(other) && (gender == null || other.gender != null || other.animal != null);
  }

  private static boolean ages(String a, String b) {
    if ("CHILD".equals(a)) {
      // 아이에게는 아이로 확인된 모습만 쓴다. 나이를 모르는 어른 무림 고수 모습이 어린 소협에 붙어 어른으로 그려졌다(회차 10 mystery-08)
      return "CHILD".equals(b);
    }
    if (a == null || b == null || a.equals(b)) {
      return true;
    }
    if ("ADULT".equals(a) || "ADULT".equals(b)) {
      return !"CHILD".equals(a) && !"CHILD".equals(b);
    }
    return false;
  }
}
