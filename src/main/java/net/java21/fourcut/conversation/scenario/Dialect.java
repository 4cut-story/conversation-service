package net.java21.fourcut.conversation.scenario;

/**
 * 말풍선 대사에 입힐 사투리. 사용자가 만들기 화면에서 고른다. NONE이면 사투리로 바꾸지 않는다(대화의 실제 말투는 살린다).
 *
 * <p>테마(컨셉) 말투와 함께 쓰면 테마의 분위기·호칭·낱말은 살리고, 어미와 말끝·억양은 사투리로 바꾼다.
 *
 * @param label 화면과 프롬프트에 쓰는 이름
 * @param rule 콘티 LLM이 대사를 이 사투리로 바꾸는 규칙
 */
public enum Dialect {
  NONE("없음", ""),
  GYEONGSANG(
      "경상도",
      """
      경상도 사투리. 어미를 "~나?", "~노?", "~다이가", "~카이", "~데이", "~해라 마", "~아이가"로, "뭐 해?" →
        "뭐 하노?", "왜?" → "와?", "진짜?" → "참말이가?", "그래서" → "그래가". 무뚝뚝하고 짧게 툭 던지는 말맛을 살린다.\
      """),
  JEOLLA(
      "전라도",
      """
      전라도 사투리. 어미를 "~잉", "~당께", "~랑께", "~허요", "~해부러", "~거시기", "~냐?" → "~냐잉?"으로,
        "뭐 해?" → "뭐 한당가?", "그러니까" → "긍께", "진짜" → "참말로", "많이" → "겁나". 구수하고 정 많은 말맛을 살린다.\
      """),
  CHUNGCHEONG(
      "충청도",
      """
      충청도 사투리. 어미를 "~유", "~슈", "~했슈", "~혀", "~겨?", "~하는겨"로 늘여 쓰고, "빨리 해" →
        "천천히 혀유~", "그래?" → "그려?", "괜찮아" → "괜찮여". 느긋하고 능청스러운 말맛을 살린다.\
      """),
  GANGWON(
      "강원도",
      """
      강원도 사투리. 어미를 "~드래요", "~했드래", "~하우", "~하나?", "~잖소"로, "뭐 해?" → "뭐 하나?",
        "그렇대" → "그렇드래요", "왜 그래?" → "왜 그러우?". 순박하고 투박한 말맛을 살린다.\
      """),
  SEOUL(
      "서울",
      """
      옛 서울 사투리. 말끝을 "~했니?", "~하니?", "~잖니", "~거든?"으로 하고, 모음을 바꿔("하고" → "하구",
        "그리고" → "그리구", "몰라" → "몰러", "삼촌" → "삼춘") 쓰며, "얘", "얘는~" 같은 부름말을 붙인다.
        새침하고 깍쟁이 같은 말맛을 살린다.\
      """);

  private final String label;
  private final String rule;

  Dialect(String label, String rule) {
    this.label = label;
    this.rule = rule;
  }

  public String label() {
    return label;
  }

  public String rule() {
    return rule.strip();
  }

  /** 이름으로 찾는다. 없거나 모르는 이름이면 NONE. */
  public static Dialect of(String name) {
    if (name == null) {
      return NONE;
    }
    for (Dialect d : values()) {
      if (d.name().equals(name)) {
        return d;
      }
    }
    return NONE;
  }
}
