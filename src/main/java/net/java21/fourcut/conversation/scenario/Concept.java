package net.java21.fourcut.conversation.scenario;

import java.util.List;

/**
 * 만화 컨셉: 그림체와 인물 표현, 대사 말투. 콘티 LLM에는 작가 페르소나·외모(look) 규칙·말투를, 그림 모델에는 그림체 문구를 준다.
 *
 * <p>특정 스튜디오·작가 이름은 프롬프트에 쓰지 않고 그림체를 풀어 쓴다.
 *
 * @param label 화면에 보이는 이름
 * @param style 그림 프롬프트 맨 앞에 붙는 그림체 (CLIP은 앞 77토큰만 읽으므로 짧게)
 * @param negative 기본 negative 뒤에 붙이는 말 (의인화 컨셉은 사람이 그려지지 않게)
 * @param crowd 두 명 이상일 때 "two ___" 자리에 쓰는 말 (의인화 컨셉은 사람이 섞이지 않게)
 * @param friend 등장인물에 없는 자리 표시({C})를 바꿀 말
 * @param lookRule 콘티 LLM이 인물 외모(look)를 쓰는 규칙
 * @param writer 콘티 LLM이 맡는 작가 페르소나(이 컨셉 만화를 그리는 사람)
 * @param voice 말풍선 대사를 다듬는 말투 규칙
 */
public enum Concept {
  DAILY(
      "일상 웹툰",
      "simple cartoon, thick outlines, flat colors, simple background",
      "",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. 나이와 성별로 시작한다: 어른은 "young woman", "young man", 할머니·할아버지는
        "old woman", "old man", 어린아이는 "girl", "boy". 머리 하나와 옷 하나를 색과 함께 쓴다
        (예: "young woman, long black hair, yellow hoodie").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다.\
      """,
      """
      너는 친구들의 일상을 짧고 경쾌한 4컷으로 그리는 일상 웹툰 작가다. 실제로 오간 말의 맛을 살려
        "맞아, 딱 이랬지" 하는 공감 웃음을 만든다.\
      """,
      """
      요즘 친구끼리 쓰는 자연스러운 구어체. 실제 말투(반말·존댓말, 사투리, 감탄사)를 그대로 살리고, "음",
        "어", "그니까" 같은 군더더기와 같은 말 반복만 덜어 말풍선에 맞게 짧고 리듬 있게 다듬는다.\
      """),
  ANIMALS(
      "동물 친구들",
      "storybook scene, cute anthropomorphic animal characters wearing clothes, round shapes,"
          + " pastel colors, simple background",
      "human, person, realistic animal, insect, bug, character sheet, sticker sheet, emoji, icons,"
          + " doodles, repeated faces, pattern",
      "animal characters, no humans",
      "another animal",
      """
      그림용 영어 6단어 이내. 인물마다 서로 다른 동물 하나(rabbit, deer, fox, bear, cat, dog, squirrel,
        panda)로 시작하고 색 있는 옷이나 소품 하나를 붙인다(예: "bear, green sweater"). 누구나 아는 귀여운
        동물만 쓰고 곤충·벌레·파충류는 쓰지 않는다. 성격에 어울리는 동물을 고른다. 옷·소품 색은 인물끼리 모두
        달라야 한다.\
      """,
      """
      너는 숲속 동물 친구들의 하루를 그리는 귀여운 동물 만화 작가다. 사람들의 대화를 동물 친구들이 나누는
        말랑하고 사랑스러운 이야기로 옮긴다.\
      """,
      """
      동물 친구들의 귀엽고 말랑한 말투. 짧은 반말, 끝을 둥글게("~했어!", "~지롱", "~거야?"), 어울릴 때만
        의성어·의태어 한 개(냠냠, 폴짝, 흥!)를 붙인다. 퉁명스러운 말도 귀여운 투정으로 바꾼다.\
      """),
  WATERCOLOR(
      "수채화 애니메이션",
      "hand-painted anime style, watercolor background, soft warm light, gentle colors",
      "",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. 나이와 성별로 시작한다: 어른은 "young woman", "young man", 할머니·할아버지는
        "old woman", "old man", 어린아이는 "girl", "boy". 머리 하나와 옷 하나를 색과 함께 쓴다
        (예: "young man, short brown hair, white shirt").
        실제 외모는 추측하지 않는다. 인물끼리 머리색·옷 색이 모두 달라야 한다.\
      """,
      """
      너는 계절과 빛, 따뜻한 감정을 담는 감성 애니메이션 작가다. 평범한 대화도 오래 기억에 남는 한 장면처럼
        잔잔하고 서정적으로 그린다.\
      """,
      """
      부드럽고 서정적인 말투. 말끝을 차분하게 맺고("~했었지", "~였어"), 쉼표나 말줄임표로 여운을 남긴다.
        들뜬 말도 따뜻한 감정이 묻어나게 다듬는다. 과장된 감탄사는 줄인다.\
      """),
  PICTURE_BOOK(
      "어린이 그림책",
      "children's picture book, crayon and colored pencil drawing, simple shapes, white paper",
      "",
      "people",
      "another child",
      """
      그림용 영어 6단어 이내. 아이는 "little girl", "little boy"로 시작하고, 선생님·엄마·아빠 같은 어른은
        "woman", "man", 할머니·할아버지는 "old woman", "old man"으로 시작한다. 머리 하나와 옷 하나를 색과 함께
        쓴다(예: "little girl, pigtails, red dress", "woman, brown bun, green apron"). 인물끼리 머리 모양·옷 색이
        모두 달라야 한다.\
      """,
      """
      너는 아이들이 소리 내어 읽는 그림책 작가다. 어떤 이야기든 아이가 바로 알아듣는 쉽고 다정한 말로
        들려준다.\
      """,
      """
      그림책 낭독처럼 쉽고 다정한 말투. 아이도 아는 쉬운 낱말과 짧은 문장, 다정한 해요체나 부드러운 반말
        ("~했어요", "~할까?"). 어려운 말·유행어·줄임말은 쉬운 말로 풀어 쓴다.\
      """),
  NEWSPAPER(
      "흑백 신문 만화",
      "black and white ink illustration, clean pen lines, light hatching, white paper,"
          + " simple background",
      "color, colorful, red, blue, halftone, screentone, dot pattern, film grain, noise,"
          + " scan lines, glitch, blurry, signboard, distorted face",
      "people",
      "a friend",
      """
      그림용 영어 외모 6단어 이내. 나이와 성별로 시작한다: 어른은 "young woman", "young man", 할머니·할아버지는
        "old woman", "old man", 아이는 "girl", "boy". 흑백이라 색 대신
        머리 모양과 눈에 띄는 소품으로 구분한다(예: "young man, curly hair, round glasses"). 인물끼리 머리
        모양·소품이 모두 달라야 한다.\
      """,
      """
      너는 신문 한 귀퉁이에 실리는 흑백 시사만화를 수십 년째 그려 온 만화가다. 짧고 건조한 한마디로 상황의
        아이러니를 꼬집어 독자를 피식 웃게 한다.\
      """,
      """
      신문 시사만화의 짧고 건조한 위트. 군더더기 없이 단문으로 끊고, 끝맺음은 담담하게("~군.", "~다니.",
        "역시."). 결 컷 대사는 상황을 비트는 한 방으로 다듬는다. 대놓고 웃기려는 감탄사는 쓰지 않는다.\
      """),
  JOSEON(
      "조선시대 사람들",
      "korean traditional genre painting, joseon era village, ink lines with soft color wash,"
          + " hanji paper texture",
      "modern clothes, car, building, smartphone, computer",
      "people in hanbok",
      "a villager",
      """
      그림용 영어 외모 6단어 이내. 조선시대 사람으로 그린다. 나이와 함께 "young woman in hanbok", "young man in
        hanbok"으로 시작하고(노인은 "old woman in hanbok", "old man in hanbok", 아이는 "girl in hanbok", "boy in
        hanbok") 머리 모양이나 쓰개 하나와 한복 색 하나를 쓴다(예: "young man in hanbok, black gat, blue jeogori",
        "old woman in hanbok, white bun, gray chima"). 인물끼리 한복 색이 모두 달라야 한다.
        scene에 나오는 현대 물건·장소는 조선시대 것으로 바꿔 쓴다(휴대폰 → letter, 택배 상자 → cloth bundles,
        거실 → hanok room, 식당 → jumak tavern).\
      """,
      """
      너는 조선시대 저잣거리 이야기꾼(전기수)이자 풍속화가다. 오늘의 대화를 조선 마을 사람들의 정겨운 옛이야기로
        옮겨, 사극을 보듯 재미있게 들려준다.\
      """,
      """
      사극 말투. 하오체·하게체("~하오", "~구려", "~소", "~하게", "~이옵니다")와 옛 호칭(자네, 이보게,
        나리, 아씨)을 쓴다. 현대 낱말은 조선 것으로 바꾼다(휴대폰 → 서찰, 택배 → 봇짐, 식당 → 주막, 돈 →
        엽전, 회사 → 관아). 웃긴 말은 해학으로 살린다.\
      """),
  WUXIA(
      "무협",
      "wuxia martial arts comic, dynamic ink brush lines, flowing robes, misty mountains,"
          + " dramatic lighting",
      "modern clothes, car, building, smartphone, computer, gun",
      "martial artists",
      "a wandering swordsman",
      """
      그림용 영어 외모 6단어 이내. 무협 소설 속 무림 고수로 그린다. 나이와 함께 "young swordswoman",
        "young swordsman"으로 시작하고(노인은 "old swordswoman", "old swordsman", 아이는 "little swordswoman",
        "little swordsman") 도포 색 하나와 무기나 소품 하나를 쓴다(예: "young swordsman, white robe, long sword",
        "old swordsman, gray robe, wooden staff"). 인물끼리 도포 색이 모두 달라야 한다.
        일상 이야기를 무림의 큰일처럼 과장해 그린다. scene의 현대 물건·장소는 무협 세계 것으로 바꿔 쓴다
        (휴대폰 → secret scroll, 택배 상자 → treasure chests, 거실 → martial arts hall, 식당 → inn).\
      """,
      """
      너는 강호의 은원을 그리는 무협지 작가다. 친구들의 사소한 일상을 무림의 운명이 걸린 대결처럼 비장하고
        과장되게 그려, 진지할수록 웃긴 무협 만화를 만든다.\
      """,
      """
      무협지 말투. 하오체와 무협 어휘("~하오", "~이오", "~구려", 소협, 대협, 본좌, 이 몸, 강호, 무림,
        내공, 비급, 일수, 승부를 내자)를 쓰고 비장하게 과장한다. 현대 낱말은 무협 세계 것으로 바꾼다(휴대폰 →
        전서구, 회사 → 문파, 상사 → 장문인, 식당 → 객잔, 내기 → 결투). 웃긴 말일수록 더 진지하게 말한다.\
      """);

  /** 동물 컨셉에서 인물마다 코드로 나눠 주는 동물(누구나 아는 귀여운 동물만). 이웃한 두 동물은 생김새가 달라야 그림 모델이 섞지 않는다(사슴·여우가 섞였다). */
  public static final List<String> ANIMAL_POOL =
      List.of("rabbit", "bear", "fox", "panda", "cat", "deer", "dog", "squirrel");

  private final String label;
  private final String style;
  private final String negative;
  private final String crowd;
  private final String friend;
  private final String lookRule;
  private final String writer;
  private final String voice;

  Concept(
      String label,
      String style,
      String negative,
      String crowd,
      String friend,
      String lookRule,
      String writer,
      String voice) {
    this.label = label;
    this.style = style;
    this.negative = negative;
    this.crowd = crowd;
    this.friend = friend;
    this.lookRule = lookRule;
    this.writer = writer;
    this.voice = voice;
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

  /** 콘티 LLM이 맡는 작가 페르소나. */
  public String writer() {
    return writer.strip();
  }

  /** 말풍선 대사를 다듬는 말투 규칙. */
  public String voice() {
    return voice.strip();
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
