package net.java21.fourcut.conversation.scenario;

/**
 * 만화 컨셉: 그림체와 인물 표현. 콘티 LLM에는 외모(look) 규칙을, 그림 모델에는 그림체 문구를 준다.
 *
 * <p>특정 스튜디오·작가 이름은 프롬프트에 쓰지 않고 그림체를 풀어 쓴다.
 *
 * @param label 화면에 보이는 이름
 * @param style 그림 프롬프트 맨 앞에 붙는 그림체 (CLIP은 앞 77토큰만 읽으므로 짧게)
 * @param negative 기본 negative 뒤에 붙이는 말 (의인화 컨셉은 사람이 그려지지 않게)
 * @param lookRule 콘티 LLM이 인물 외모(look)를 쓰는 규칙
 */
public enum Concept {
  DAILY(
      "일상 웹툰",
      "simple cartoon, thick outlines, flat colors, plain background",
      "",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고(대화가 아이들일
        때만 girl, boy), 머리 하나와 옷 하나를 색과 함께 쓴다(예: "young woman, long black hair, yellow hoodie").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다."""),
  INSECTS(
      "곤충 친구들",
      "cute storybook illustration, anthropomorphic insect characters, big eyes, soft colors,"
          + " simple background",
      "human, person, realistic insect, scary",
      """
      그림용 영어 6단어 이내. 인물마다 서로 다른 곤충 하나(ladybug, honeybee, ant, butterfly, beetle,
        grasshopper, snail)로 시작하고 색 있는 소품 하나를 붙인다(예: "ladybug, yellow scarf").
        성격에 어울리는 곤충을 고른다. 소품 색은 인물끼리 모두 달라야 한다."""),
  ANIMALS(
      "동물 친구들",
      "cute anthropomorphic animal characters, round shapes, pastel colors, simple background",
      "human, person, realistic animal",
      """
      그림용 영어 6단어 이내. 인물마다 서로 다른 동물 하나(cat, bear, rabbit, fox, dog, penguin, hamster)로
        시작하고 색 있는 옷이나 소품 하나를 붙인다(예: "bear, green sweater"). 성격에 어울리는 동물을 고른다.
        옷·소품 색은 인물끼리 모두 달라야 한다."""),
  WATERCOLOR(
      "수채화 애니메이션",
      "hand-painted anime style, watercolor background, soft warm light, gentle colors",
      "",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고(대화가 아이들일
        때만 girl, boy), 머리 하나와 옷 하나를 색과 함께 쓴다(예: "young man, short brown hair, white shirt").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다."""),
  PICTURE_BOOK(
      "어린이 그림책",
      "children's picture book, crayon and colored pencil drawing, simple shapes, white paper",
      "",
      """
      그림용 영어 6단어 이내. "little girl", "little boy"로 시작하고 머리 하나와 옷 하나를 색과 함께 쓴다
        (예: "little girl, pigtails, red dress"). 인물끼리 머리 모양·옷 색이 모두 달라야 한다."""),
  NEWSPAPER(
      "흑백 신문 만화",
      "black and white newspaper comic strip, ink pen lines, cross hatching",
      "color, colorful",
      """
      그림용 영어 외모 6단어 이내. "young woman", "young man"처럼 어른 성별 명사로 시작하고, 흑백이라 색 대신
        머리 모양과 눈에 띄는 소품으로 구분한다(예: "young man, curly hair, round glasses"). 인물끼리 머리
        모양·소품이 모두 달라야 한다.""");

  private final String label;
  private final String style;
  private final String negative;
  private final String lookRule;

  Concept(String label, String style, String negative, String lookRule) {
    this.label = label;
    this.style = style;
    this.negative = negative;
    this.lookRule = lookRule;
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
