package net.java21.fourcut.conversation.scenario;

import java.util.List;

/**
 * 만화 컨셉: 그림체와 인물 표현. 콘티 LLM에는 외모(look) 규칙을, 그림 모델에는 그림체 문구를 준다.
 *
 * <p>특정 스튜디오·작가 이름은 프롬프트에 쓰지 않고 그림체를 풀어 쓴다.
 *
 * @param label 화면에 보이는 이름
 * @param style 그림 프롬프트 맨 앞에 붙는 그림체 (CLIP은 앞 77토큰만 읽으므로 짧게)
 * @param negative 기본 negative 뒤에 붙이는 말 (의인화 컨셉은 사람이 그려지지 않게)
 * @param crowd 두 명 이상일 때 "two ___" 자리에 쓰는 말 (의인화 컨셉은 사람이 섞이지 않게)
 * @param friend 등장인물에 없는 자리 표시({C})를 바꿀 말
 * @param lookRule 콘티 LLM이 인물 외모(look)를 쓰는 규칙
 * @param voice 대사 말투 규칙. 비어 있으면 대화의 말을 그대로 쓴다
 */
public enum Concept {
  DAILY(
      "일상 웹툰",
      "simple cartoon, thick outlines, flat colors, plain background",
      "",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고(대화가 아이들일
        때만 girl, boy), 머리 하나와 옷 하나를 색과 함께 쓴다(예: "young woman, long black hair, yellow hoodie").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다.""",
      ""),
  ANIMALS(
      "동물 친구들",
      "cute anthropomorphic animal characters, round shapes, pastel colors, simple background",
      "human, person, realistic animal, insect, bug",
      "animal characters, no humans",
      "another animal",
      """
      그림용 영어 6단어 이내. 인물마다 서로 다른 동물 하나(rabbit, deer, fox, bear, cat, dog, squirrel,
        panda)로 시작하고 색 있는 옷이나 소품 하나를 붙인다(예: "bear, green sweater"). 누구나 아는 귀여운
        동물만 쓰고 곤충·벌레·파충류는 쓰지 않는다. 성격에 어울리는 동물을 고른다. 옷·소품 색은 인물끼리 모두
        달라야 한다.""",
      "동화 속 동물 친구 말투. 다정하고 귀여운 말끝(~했어요, ~할래?, ~구나!)을 쓰고 짧게 말한다."),
  WATERCOLOR(
      "수채화 애니메이션",
      "hand-painted anime style, watercolor background, soft warm light, gentle colors",
      "",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고(대화가 아이들일
        때만 girl, boy), 머리 하나와 옷 하나를 색과 함께 쓴다(예: "young man, short brown hair, white shirt").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다.""",
      ""),
  PICTURE_BOOK(
      "어린이 그림책",
      "children's picture book, crayon and colored pencil drawing, simple shapes, white paper",
      "",
      "children",
      "another child",
      """
      그림용 영어 6단어 이내. "little girl", "little boy"로 시작하고 머리 하나와 옷 하나를 색과 함께 쓴다
        (예: "little girl, pigtails, red dress"). 인물끼리 머리 모양·옷 색이 모두 달라야 한다.""",
      "어린이 그림책 말투. 아이도 읽기 쉬운 쉬운 낱말과 짧은 문장, 다정한 말끝(~했어, ~할까?, ~야!)을 쓴다."),
  NEWSPAPER(
      "흑백 신문 만화",
      "black and white newspaper comic strip, ink pen lines, cross hatching",
      "color, colorful, red, blue",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고, 흑백이라 색 대신
        머리 모양과 눈에 띄는 소품으로 구분한다(예: "young man, curly hair, round glasses"). 인물끼리 머리
        모양·소품이 모두 달라야 한다.""",
      ""),
  JOSEON(
      "조선시대 사람들",
      "korean traditional genre painting, joseon era village, ink lines with soft color wash,"
          + " hanji paper texture",
      "modern clothes, car, building, smartphone, computer",
      "people in hanbok",
      "a villager",
      """
      그림용 영어 외모 6단어 이내. 조선시대 사람으로 그린다. "woman in hanbok", "man in hanbok"으로 시작하고
        머리 모양이나 쓰개 하나와 한복 색 하나를 쓴다(예: "man in hanbok, black gat, blue jeogori",
        "woman in hanbok, braided hair, pink chima"). 인물끼리 한복 색이 모두 달라야 한다.
        scene에 나오는 현대 물건·장소는 조선시대 것으로 바꿔 쓴다(휴대폰 → letter, 택배 상자 → cloth bundles,
        거실 → hanok room, 식당 → jumak tavern).""",
      "조선시대 사극 말투. 하오체·하게체(~하오, ~하시오, ~구려, ~이네, ~하게)와 옛말(어찌, 허허, 그대, 소인,"
          + " 나리)을 쓴다. 현대 물건·말은 조선시대 것으로 바꾼다(휴대폰 → 서찰, 택배 → 봇짐, 식당 → 주막, 돈 → 엽전)."),
  WUXIA(
      "무협",
      "wuxia martial arts comic, dynamic ink brush lines, flowing robes, misty mountains,"
          + " dramatic lighting",
      "modern clothes, car, building, smartphone, computer, gun",
      "martial artists",
      "a wandering swordsman",
      """
      그림용 영어 외모 6단어 이내. 무협 소설 속 무림 고수로 그린다. "swordswoman", "swordsman"으로
        시작하고 도포 색 하나와 무기나 소품 하나를 쓴다(예: "swordsman, white robe, long sword",
        "swordswoman, red robe, folding fan"). 인물끼리 도포 색이 모두 달라야 한다.
        일상 이야기를 무림의 큰일처럼 과장해 그린다. scene의 현대 물건·장소는 무협 세계 것으로 바꿔 쓴다
        (휴대폰 → secret scroll, 택배 상자 → treasure chests, 거실 → martial arts hall, 식당 → inn).""",
      "무협 소설 말투. 하오체와 무림 말(~하오, 본좌, 소협, 대협, 과연, 감히!, 크윽)을 쓰고 일상 일을 무림의"
          + " 큰일처럼 비장하게 말한다. 현대 물건·말은 무협 세계 것으로 바꾼다(휴대폰 → 비급, 택배 → 보물 상자,"
          + " 식당 → 객잔, 돈 → 은자).");

  /** 동물 컨셉에서 인물마다 코드로 나눠 주는 동물(누구나 아는 귀여운 동물만). 이웃한 두 동물은 생김새가 달라야 그림 모델이 섞지 않는다(사슴·여우가 섞였다). */
  public static final List<String> ANIMAL_POOL =
      List.of("rabbit", "bear", "fox", "panda", "cat", "deer", "dog", "squirrel");

  private final String label;
  private final String style;
  private final String negative;
  private final String crowd;
  private final String friend;
  private final String lookRule;
  private final String voice;

  Concept(
      String label,
      String style,
      String negative,
      String crowd,
      String friend,
      String lookRule,
      String voice) {
    this.label = label;
    this.style = style;
    this.negative = negative;
    this.crowd = crowd;
    this.friend = friend;
    this.lookRule = lookRule;
    this.voice = voice;
  }

  /** 대사 말투 규칙. 빈 문자열이면 대화의 말을 그대로 쓴다. */
  public String voice() {
    return voice;
  }

  public String crowd() {
    return crowd;
  }

  public String friend() {
    return friend;
  }

  public String label() {
    return label;
  }

  public String style() {
    return style;
  }

  public String negative() {
    return negative;
  }

  public String lookRule() {
    return lookRule.strip();
  }

  /** 사용자가 고른 컨셉이 있으면 그것, 없으면(자동) 장면에 맞게 고른 컨셉. */
  public static Concept chosen(String user, String suggested) {
    return of(user != null ? user : suggested);
  }

  /** 이름으로 찾는다. 없거나 모르는 값이면 기본(DAILY). */
  public static Concept of(String name) {
    for (Concept c : values()) {
      if (c.name().equalsIgnoreCase(name == null ? "" : name.strip())) {
        return c;
      }
    }
    return DAILY;
  }
}
