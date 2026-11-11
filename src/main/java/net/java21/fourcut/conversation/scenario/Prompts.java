package net.java21.fourcut.conversation.scenario;

/**
 * LLM 프롬프트와 출력 JSON 스키마. 바꾸면 VERSION을 올린다 (단계 기록 model_info에 남는다).
 *
 * <p>qwen3 thinking은 워커가 끈다(think=false). 그래서 "속으로 여러 안을 만들어 고르라" 같은 지시는 두지 않는다. 맞춤법·실명 검수도 콘티 호출 한
 * 번에 함께 시킨다(LLM 호출 1회 절약).
 */
final class Prompts {

  static final String VERSION = "2026-11-11.34";

  private Prompts() {}

  /** 장면 후보 JSON(후보 3개, 핵심 순간, 항목 점수, 이름)은 짧다. 넘치면 잘린 JSON이 되어 재시도한다. */
  static final int EXTRACT_MAX_TOKENS = 1300;

  /** 콘티 JSON(컷 4~10개, 원문 대사와 다듬은 대사, 인물) 출력 상한. 4컷에 2600이던 것을 10컷까지 들어가게 늘렸다. */
  static final int SCRIPT_MAX_TOKENS = 6000;

  static final String EXTRACT_SYSTEM =
      """
      대화 전사에서 만화로 남길 특별한 순간을 찾는다. 대화한 사람들이 나중에 만화를 보고 "맞아, 그때 그랬지" 하며
      다시 웃거나 마음이 따뜻해질 장면이다.

      특별한 순간의 신호
      - 웃음이 터진다(ㅋㅋ, 하하, "아 미치겠다"), 여러 사람이 한꺼번에 반응하거나 "헐", "진짜?", "대박"처럼 놀란다.
      - 예상이 뒤집힌다: 허세가 들통난다, 실수, 엉뚱한 오해, 어이없는 우연.
      - 진심이 오간다: 고마움, 응원, 위로, 사과, 고백. 소식·결정·약속이 나온다.
      - 함께 겪은 지난 일을 떠올리며 이야기가 이어진다.
      - 함께 보낸 하루가 즐겁고 따뜻하다: 가족·친구를 찾아가 함께 먹고, 놀고, 구경한 일. 큰 웃음이나 반전이 없어도 기록으로
        남기고 싶은 하루면 장면이 된다.
      신호가 나온 줄을 중심으로, 그 일이 시작된 줄부터 반응이 가라앉는 줄까지를 한 장면으로 잡는다.

      고르지 않는 것
      - 인사, 안부, 자리 잡기, 늦은 사과, 메뉴 고르기·주문·계산 같은 진행 대화.
      - 반응이나 결말 없이 정보만 오가는 잡담, 한 사람이 혼자 길게 설명만 하는 구간.
      - 욕설·민감한 주제(건강, 정치, 성, 혐오, 개인정보)가 중심인 장면.

      출력
      - 최대 3개, 서로 겹치지 않게, 가장 특별한 장면부터. 특별한 순간이 없으면 빈 목록이 낫다.
      - kind: FUNNY(즐거운 대화: 웃음, 반전, 엉뚱한 말, 티격태격), TOUCHING(남기고 싶은 대화: 진심, 고마움, 응원, 위로, 함께 보낸 따뜻한 하루),
        IMPORTANT(중요한 내용: 결정, 약속, 소식, 계획), MEMORY(추억: 함께 겪은 지난 일을 떠올리는 이야기),
        CORE(위에 맞지 않지만 이 대화가 가장 오래·깊게 다룬 주제).
      - from, to: 장면 첫 줄과 마지막 줄 앞의 [분:초] 표시를 그대로 옮긴다(예: "04:11"). 장면 길이는 30초~3분.
      - peak: 이 장면을 특별하게 만드는 한 마디. [from, to] 안에 있는 말을 고쳐 쓰지 말고 짧게 옮긴다(40자 이내).
      - summary 한 줄: 무슨 상황에서 어떤 일이 생겨 어떻게 됐는지. 그 일이 실제로 from~to 줄 안에 나와야 한다.
        reason 한 줄: 대화한 사람들에게 왜 특별한지. 사람은 이름 대신 화자 표시(A, B…)로 쓴다.
      - flow 한 줄: 이 장면을 만화로 그릴 때 무엇을 어떻게 살릴지. 정해진 목록은 없다. 장면이 가진 포인트를 보고 네가 정한다.
        예) "허세가 들통나는 순간을 크게 웃기게", "할아버지 집에서 보낸 하루를 억지 반전 없이 따뜻하게 기록", "고마움을
        말하는 순간의 감동을 천천히", "황당한 핑계를 꼬집는 풍자", "서로 응원하며 결심하는 다짐". 입력에 "기사 흐름"이 있으면
        그 흐름을 따른다.
      - 항목 점수 1~5. 후하게 주지 않는다. 5는 이 대화에서 손꼽히는 장면에만 준다.
        story 이야기성: 상황 → 사건 → 반응 → 결말이 이 구간 안에 다 나오는가.
        visual 그림: 행동, 장소, 물건이 있어 그림으로 보여 줄 수 있는가.
        relatable 공감: 대화한 사람들이 보고 "맞아, 이랬지" 할 장면인가.
        memorable 간직: 몇 년 뒤에도 꺼내 보고 싶은가. 웃음이 크게 터졌거나 마음이 움직였으면 높고, 평범한 잡담은 1~2.
      - intro: 인사, 안부, 자리 잡기, 늦은 사과처럼 본론 전 도입부면 true.
      - concept: 이 장면에 어울리는 그림 컨셉 하나. DAILY(일상 웹툰: 친구·회사·일상 이야기, 뉴스·사회 이야기와 풍자도 기본은 여기), ANIMALS(동물 친구들: 귀엽고 가벼운 다툼·먹는 이야기), WATERCOLOR(수채화
        애니메이션: 추억, 여행, 계절, 따뜻한 감정), PICTURE_BOOK(어린이 그림책: 가족, 아이, 소박한 하루),
        NEWSPAPER(흑백 신문 만화: 정치·행정을 꼬집는 시사 만평처럼 흑백 한 장이 꼭 어울릴 때만. 풍자나 사회 이야기라는
        이유만으로 고르지 않는다), JOSEON(조선시대 사람들: 옛날이야기처럼
        풀면 재미있는 가족·이웃·장터 이야기), WUXIA(무협: 내기, 승부, 비밀, 사소한 일을 크게 부풀린 대결).
      - names: 대화에서 사람 이름(실명·별명)으로 쓰인 단어만 최대 6개. 조사·호격을 뺀 형태로 쓴다(민지야 → 민지,
        준호는 → 준호). 직함·호칭(팀장, 사장님, 언니), 음식, 물건, 장소, 가게 이름은 넣지 않는다. 없으면 빈 목록.
      - entities: 대화에 나온 실존 기업·브랜드·정당·단체 이름과 실존 인물(정치인, 연예인, 운동선수, 기업 회장·총수 등)
        이름, 소속과 직함을 붙여 한 사람을 가리키는 말(예: "OO그룹 회장")을 최대 8개. name은 대화에 나온 그대로(조사는
        뺀다), generic은 누구인지 알 수 없는 일반 표현(10자 이내)이다. 예) 삼성전자 → 대기업, 스타벅스 → 카페, 이OO 회장
        → 회장님, OO당 → 한 정당, 아이유 → 한 가수. 대화한 사람들의 이름(names)은 넣지 않는다. 없으면 빈 목록.
      - 대화와 같은 언어로 쓴다.
      """;

  static final String EXTRACT_SCHEMA =
      """
      {"type":"object","required":["candidates","names"],"properties":{
       "candidates":{"type":"array","maxItems":3,
       "items":{"type":"object",
        "required":["from","to","peak","kind","summary","reason","flow","story","visual","relatable","memorable","intro","concept"],
        "properties":{"from":{"type":"string","pattern":"^[0-9]{2,3}:[0-9]{2}$"},"to":{"type":"string","pattern":"^[0-9]{2,3}:[0-9]{2}$"},
         "peak":{"type":"string"},
         "kind":{"type":"string","enum":["FUNNY","TOUCHING","IMPORTANT","MEMORY","CORE"]},
         "summary":{"type":"string"},"reason":{"type":"string"},"flow":{"type":"string"},
         "story":{"type":"integer","minimum":1,"maximum":5},"visual":{"type":"integer","minimum":1,"maximum":5},
         "relatable":{"type":"integer","minimum":1,"maximum":5},"memorable":{"type":"integer","minimum":1,"maximum":5},
         "intro":{"type":"boolean"},
         "concept":{"type":"string","enum":["DAILY","ANIMALS","WATERCOLOR","PICTURE_BOOK","NEWSPAPER","JOSEON","WUXIA"]}}}},
       "names":{"type":"array","maxItems":6,"items":{"type":"string"}},
       "entities":{"type":"array","maxItems":8,"items":{"type":"object","required":["name","generic"],
        "properties":{"name":{"type":"string"},"generic":{"type":"string"}}}}}}
      """;

  /** 장면 종류마다 콘티의 흐름과 분위기. 콘티 입력의 "만화 분위기"로 넣는다. */
  static String tone(String kind) {
    return switch (kind == null ? "" : kind) {
      case "FUNNY" -> "웃음. 기·승에서 복선을 깔고, 전에서 웃음 포인트(반전·들통·엉뚱한 말)가 터지고, 결은 모두의 반응이나 한 방 더 웃긴 마무리.";
      case "TOUCHING" ->
          "진심·따뜻한 하루. 억지 반전을 넣지 않는다. 그 순간의 구체적인 모습(장소, 먹은 것, 한 일, 표정)을 살리고, 전에서 진심이 담긴 말이나 가장 즐거운 순간을 크게 보여 주고, 결은 서로의 표정과 따뜻한 여운.";
      case "IMPORTANT" -> "소식·결정. 전에서 소식이나 결정이 나오는 순간과 듣는 사람의 반응, 결은 그 뒤의 기대나 다짐.";
      case "MEMORY" -> "추억. 기는 지금 자리, 승·전은 함께 떠올린 그때 일, 결은 다시 지금으로 돌아와 함께 웃는 여운.";
      default -> "이 대화의 중심 이야기. 전에서 이야기가 가장 크게 움직이는 순간, 결은 결론과 여운.";
    };
  }

  /** 컨셉에 맞는 콘티 지시. 컨셉마다 작가 페르소나, 인물 외모(look) 규칙, 대사 말투가 다르다. */
  static String scriptSystem(Concept concept) {
    return scriptSystem(concept, Dialect.NONE);
  }

  /** 컨셉 콘티 지시에 사용자가 고른 사투리 규칙을 더한다. NONE이면 사투리 규칙이 없다. */
  static String scriptSystem(Concept concept, Dialect dialect) {
    return SCRIPT_TEMPLATE
        .replace("%DIALECT%", dialectRule(dialect))
        .replace("%WRITER%", concept.writer())
        .replace("%LOOK%", concept.lookRule())
        .replace("%VOICE%", concept.voice())
        .replace("%CONCEPT%", concept.label());
  }

  private static String dialectRule(Dialect dialect) {
    if (dialect == null || dialect == Dialect.NONE) {
      return "";
    }
    return "\n- 사투리("
        + dialect.label()
        + "): 사용자가 고른 사투리다. 모든 인물의 text를 이 사투리로 바꾼다. "
        + "\"말투\"의 분위기·호칭·낱말(테마 어휘)은 살리고, 어미와 말끝·억양은 이 사투리로 한다. "
        + "뜻과 사실은 바꾸지 않고 30자 이내를 지킨다. quote는 바꾸지 않는다.\n  "
        + dialect.rule();
  }

  /** 기본(일상 웹툰) 콘티 지시. */
  static final String SCRIPT_SYSTEM = scriptSystem(Concept.DAILY);

  private static final String SCRIPT_TEMPLATE =
      """
      %WRITER%
      이 대화 장면을 "%CONCEPT%" 만화의 기-승-전-결 콘티로 만든다. 컷 수는 4~10컷 중에서 이야기에 맞게 직접 정한다.
      그림은 그림 모델이 그린다. 그림 모델은 말을 글자 그대로 읽어서, 같은 것을 두 번 쓰면 두 개로, 두 가지를 섞어 쓰면 섞어서
      그린다. 그래서 아래 규칙은 대부분 "한 컷에 한 사람, 한 동작, 물건 하나를 한 번만 쓴다"는 한 가지 원칙에서 나온다.

      인물 (characters, 화자 A, B…마다 하나)
      - name: 입력의 "인물 가명"을 그대로 쓴다. 가명은 만화 아래 등장인물 소개에만 나오고 대사·제목에는 쓰지 않는다.
      - role: 만화 속 호칭 한 낱말(8자 이내). 대화 내용과 서로 부르는 말로 관계를 짐작해 정한다(예: 할머니, 할아버지, 엄마,
        아빠, 손녀, 손자, 아들, 딸, 언니, 오빠, 형, 동생, 친구, 선배, 후배, 팀장, 동료). 모르면 "친구". 인물끼리 겹치지 않게
        한다(친구가 둘이면 "친구"와 "단짝").
      - persona: 이 장면에서 드러난 성격을 "%CONCEPT%" 세계의 인물로 한 줄(예: 먹는 데 진심인 친구). 한 인물은 모든 컷에서
        이 persona의 말투를 쓴다.
      - look: %LOOK%
        look에는 몸통을 덮는 윗옷(셔츠, 스웨터, 조끼, 도포, 저고리) 하나를 색과 함께 꼭 쓴다. 모자·안경·목도리·머리띠처럼
        몸에 쓰거나 두르는 것도 look에만 쓴다. 직업 옷(가운, 제복, 승복)은 그 일을 하는 장면이 나올 때만 쓴다.
      - bottom: 아래옷을 그림용 영어 4단어 이내로 색과 함께 쓴다(예: "blue jeans", "long navy skirt", "straight-leg white
        trousers"). 바지는 "straight-leg"·"jeans"처럼 두 다리가 따로 보이는 말로 쓴다("wide trousers"는 치마로 그려진다).
        한 벌 옷(dress, robe, overalls)이면 그 아래 보이는 것을, 바지를 입지 않은 동물은 "bare fur legs"를 쓴다.
      - shoes: 신발을 그림용 영어 3단어 이내로 색과 함께 쓴다(예: "white sneakers", "black cloth shoes"). 집 안 장면이 많은
        인물은 양말이나 실내화로 쓴다(예: "white socks", "gray slippers"). "bare feet"는 쓰지 않는다(발가락이 뭉개진다).
      - 인물끼리 한눈에 달라 보이게 한다: 윗옷 종류와 색, 머리 길이가 겹치지 않게, 동물이면 종·털색·몸집도 겹치지 않게 고른다(회색 토끼와 회색
        고양이는 한 인물로 섞인다). 동물 look에는 털색을 쓴다(예: "brown bear", "white rabbit"). 입력에 외모가 이미 정해진
        인물이 있으면 다른 인물은 그와 다르게 고른다.
      - look·bottom·shoes는 모든 컷에서 입는 한 벌이다. 컷마다 바꾸지 않는다.

      소품 (props, 0~4개, 한 번만 정한다)
      - 넣는 것: 이야기의 핵심 물건, 중심 동물(반려동물 등), 인물이 여러 컷에 들고 다니는 물건(가방, 지팡이, 책, 휴대폰).
        props에 없는 물건은 컷마다 다른 물건으로 그려진다. 몸에 쓰거나 두르는 것은 넣지 않는다(look에 쓴다).
      - key는 짧은 한국어 이름(예: 줄넘기, 연, 강아지). draw는 그림용 영어 8단어 이내로 재질·색·크기·종까지 쓴다(예: "red
        plastic jump rope with wooden handles", "small yellow diamond-shaped paper kite"). 비슷한 다른 물건으로 바뀌지 않게
        구체적으로 쓴다.
      - draw는 물건 한 개를, 쉼표 없이 한 덩어리 말로 쓴다. 부분은 with·on으로 잇는다(예: "long brass telescope on a tripod").
        그러면 안 되는 것과 그 까닭:
        · 쉼표로 나누기("telescope, one eyepiece, tripod") → 부분마다 따로 그려져 물건이 둘.
        · 여러 개나 안에 든 것("봉지 두 개", "full of dumplings") → 물건이 하나 더 그려지거나 작은 것이 옷에 붙는다. 봉투·상자·
          가방은 닫힌 모습으로 쓴다(예: "closed brown paper bag"). 많이 쌓인 것이 이야기면 바닥에 둔 더미로 쓴다(예: "heap of
          socks spilling out of {강아지집}").
        · 담는 것·받치는 것·싸는 것(접시, 쟁반, 보자기)을 함께 쓰기("toast on a plate") → 손에 하나, 접시에 하나.
        · 상태(켜짐, 열림, 화면에 보이는 것) → 그 컷 action에만 쓴다. 두 곳에 다르게 쓰면 두 개로 그려진다.
      - 개수를 못 박아야 하는 물건:
        · 한 장소에 여러 대 놓일 법한 기계(키오스크, 자판기, 세탁기, 가스레인지)와 멀리 보이는 핵심 건물·불빛(산 위 오두막,
          등대)은 "a single"로 시작한다(예: "a single tall white touchscreen kiosk").
        · 짝으로 다니는 물건(양말, 신발, 장갑, 귀걸이)이 하나뿐인 것이 이야기면 "one lone"으로 시작한다(예: "one lone yellow
          sock with red stripes").
        · 생김새가 비슷한 다른 물건이 있으면(망원경 ↔ 쌍안경) 둘을 가르는 부분의 개수를 쓴다(예: "single long brass telescope
          tube with one eyepiece on a tripod").
      - 모양이 헷갈리는 물건(판 놀이, 기계, 음식)은 전체 모양, 겉면, 작은 조각이 놓인 자리를 쓴다(예: "flat square chessboard
        of black and white squares, a few small chess pieces standing on it").
      - 반려동물 소품은 "tiny"로 시작하고 "on four legs, no clothes"를 붙인다. 동물 인물과 한 컷에 나오면 그 인물 발치에 둔다
        (예: "{강아지} sits at {A}'s feet").
      - 식물 소품(나무, 고추 모종)의 draw에는 열매를 여럿 쓰지 않는다. 딴 열매·꽃을 드는 컷은 식물 소품을 쓰지 않고 든 것
        하나만 쓴다(예: "{B} holds up one long red pepper").
      - 들고 다니는 물건은 인물마다 하나까지, 이야기에 꼭 필요할 때만 둔다. 길쭉한 물건(칼, 지팡이, 막대, 부채)은 편 전체에서
        한 인물에게만 준다.
      - 글자: 그림 모델은 가짜 글자를 그린다. 글자가 보이는 물건(간판, 팻말, 문자, 책 표지)은 글자 없이 모양과 색으로 쓰고
        (예: "blank wooden shop sign"), 병풍·액자·칠판·화면에는 "plain"을 붙인다. 화면에 보여야 할 것은 그림 하나로 쓴다
        (예: "screen showing a big burger picture"). 팻말은 받침까지 쓴다(예: "small blank wooden board nailed to one stake").
        draw·action·setting에 그림 속 글자로 들어갈 낱말(order, menu, sale, exit, open, number, 가게 이름)을 쓰지 않는다.

      장소 (places, 1~3개, 한 번만 정한다)
      - key는 짧은 한국어 이름(예: 놀이터, 세탁실). draw는 그림용 영어 14단어 이내로 장소 이름을 먼저 쓰고, 한눈에 알아보게
        하는 크고 눈에 띄는 특징 2~3개를 색·모양과 함께 붙인다(예: "small playground, yellow slide, blue swings, sandbox").
        소품에 있는 물건은 장소 draw에 쓰지 않는다(장소에 하나, 소품으로 하나 더 그려진다). 대화 내용과 시대(%CONCEPT%)에
        맞게 정한다.
      - draw 끝에 그 장소의 때와 빛을 붙인다(예: "..., morning, bright daylight through the window", "..., night, dark
        window"). 대사에 시각이 나오면 그 시각에 맞춘다(아침 8시는 밝은 아침이다. 별·달·어두운 창을 쓰지 않는다).
        때가 대사에 없으면 대화 내용에서 가장 그럴듯한 때를 고른다.
      - 이야기 속에서 실제로 옮겨 갈 때만 장소를 하나 더 둔다.
      - 장소는 이야기 속 일이 실제로 벌어지는 곳이다. 대화가 다른 곳에서 겪은 일(재판 방청, 여행, 회사 일, 시합, 뉴스 속
        사건)을 들려주는 것이면 들려주는 자리가 아니라 그 일이 벌어진 곳(법정, 여행지, 공장, 경기장, 사건 현장)을 첫 장소로
        정하고, 컷은 그 일을 겪는 모습으로 그린다. 듣는 사람의 반응은 결 컷에서 이야기 나누는 자리를 둘째 장소로 두어 그린다.
      - 집·거실·방·부엌·카페는 대화에 그 장소가 나오거나 그곳에서 일이 벌어질 때만 쓴다. 어디서 나눈 대화인지 모른다고
        집·거실·카페로 정하지 않는다. 대화에 나온 일·물건·직업·계절이 가리키는 곳(시장, 공장, 병원, 경기장, 논밭, 바닷가,
        버스 안, 공사장, 무대, 관공서 창구, 역 승강장 등)을 고른다.

      자리표: 인물은 {A}, {B}, 소품은 {연}처럼 key를 중괄호로 감싸 쓴다. action·setting·lookAt에서 인물과 소품은 자리표로만
      쓴다. 자리표는 모든 컷에 같은 외모·draw 말로 바뀌어 들어간다. 자리표 바로 뒤에 이름씨("{망원경} tube")나
      동사·분사("{청소기} stuck", "{양말} lying")를 붙이지 않는다. 물건 이름이 한 번 더 붙거나 물건이 움직이는 둘째 주인공이
      되어 둘로 그려진다.

      컷 수 (panels 4~10개)
      - 기본은 4컷이다. 4컷에 담으면 중요한 순간이 빠질 때만 늘린다. 컷이 늘면 그리는 시간도 늘어난다.
      - 늘리는 경우: 사건이 여러 단계로 이어진다, 오해·반전이 두 번 이상 있다, 여러 인물이 차례로 반응해야 재미가 산다,
        주고받은 말 자체가 웃음이다.
      - 4컷으로 두는 경우: 한 번의 반전이나 한마디가 핵심이다, 순간이 짧다, [장면 대화]에 쓸 만한 말이 적다.
      - 컷마다 서로 다른 대사가 1~2개 있어야 하므로, 이야기를 이끄는 말이 컷 수보다 적으면 늘리지 않는다.
      - 대략 짧은 일화는 4컷, 사건이 한두 번 더 이어지면 6컷, 길게 이어지면 8컷, 10컷은 정말 긴 이야기에만.

      컷 흐름 (panels, role 순서 "기","승","전","결")
      - 대화한 사람들이 간직할 기념 만화다. "장면 요약"의 이야기를 정한 컷 수만큼의 순간으로 나눈다. 기: 상황(누가 어디서
        무엇을) / 승: 사건이 생김, 전으로 가는 복선 / 전: "핵심 순간"의 말이 나오는 컷, 반전·감정이 가장 큰 순간 / 결: 결과·
        반응·여운. 분위기는 "만화 분위기"를 따르고, "이 장면의 흐름"이 있으면 무엇을 살릴지는 그것을 따른다. 컷을 차례로 보면
        이야기가 그대로 읽혀야 한다.
      - 입력에 "기사 흐름"과 "기사 핵심"이 있으면 뉴스 기사로 만든 만화다. 컷 흐름은 "흐름 단계"의 기·승·전·결을 따르고,
        "기사 핵심"의 사실이 담긴 말을 컷 대사로 골라 만화만 보고도 기사의 요점이 읽히게 한다. text로 다듬을 때도 숫자·결과·
        무슨 일인지는 빼거나 바꾸지 않는다. 풍자 흐름이면 꼬집는 대상(잘못한 행동과 구조)이 전 컷에서 분명히 드러나게 한다.
      - role: 첫 컷은 "기", 마지막 한 컷은 "결". 순서는 거꾸로 가지 않는다. 4컷이면 한 컷씩, 더 많으면 승과 전을 늘린다(기는
        두 컷까지). 예: 6컷 기·승·승·전·전·결, 8컷 기·승·승·승·전·전·전·결.
      - 모든 컷은 서로 다른 순간이다. 같은 행동·같은 구도를 반복하지 않는다.
      - 이야기의 핵심 물건은 두 컷 이상에 나온다. 컷 대사가 눈에 보이는 것을 말하면 action과 setting이 그것을 보여 준다.

      한 컷에 한 사람
      - 모든 컷은 그 컷 주인공 한 명만 그린다. 두 명을 그리면 그림 모델이 같은 사람을 둘로 그리거나 옷·얼굴·동작을 섞는다
        (자리를 정해도, 한 사람씩 나눠 그려도 바뀐다). 함께 하는 순간(건네기, 같은 것 보기, 주고받는 말, 마주 앉기)은 한 사람이
        하는 컷과 다른 사람이 반응하는 컷으로 나눈다(예: 3컷 "{A} holds up {연} at the chest", 4컷 "{B} looks up at {연},
        wide-eyed").
      - 그 컷 lines의 speaker는 그 컷 주인공이다. 상대의 대답은 상대가 나오는 다음 컷에 둔다.
      - 컷에 나오지 않는 인물은 action·setting에 쓰지 않는다. 그 인물을 보는 컷이면 lookAt만 그 자리표로 쓴다(그림에는
        그리지 않는다).
      - 말풍선 자리: 인물은 화면 아래쪽 2/3에 두고 머리 위를 비운다. 하늘·배경의 핵심 대상도 위쪽 끝에 두지 않는다.

      shot
      - wide(온몸과 장소), medium(허리 위, 다리는 화면 밖), close-up(가슴 위 얼굴) 중 하나. 기는 보통 wide, 전은 보통
        close-up이고, wide는 전체 컷의 절반을 넘지 않는다. 같은 구도가 두 컷 넘게 이어지지 않게 섞는다.
      - 다리가 보여야 하는 동작(서기, 걷기, 달리기, 무릎 꿇기)은 wide에만 쓴다. 앉은 컷은 발이 이야기가 아니면 medium으로
        쓴다(발이 화면에 들어가면 자주 뭉개진다). medium은 손·팔·얼굴 동작(들어 올리기,
        내밀기, 버튼 누르기)만, close-up은 얼굴·머리·목 동작(볼 붉히기, 입 벌리기, 고개 숙이기)만 쓴다. close-up에서는 물건을
        쥐지 않는다(물건이 잘려 두 개로 그려진다).

      - action: 그림 모델용 영어 14단어 이내. 그 컷에서 멈춘 한 순간의 몸동작 하나를 "누가 + 동작 + 무엇을" 순서로 쓴다
        (예: "{A} jumps over {줄넘기} swinging it", "{B} holds up {양말} at the chest").
        좋은 동작
        · 얼굴을 가려도 실루엣으로 읽히는 큰 동작: 두 주먹 번쩍 들기, 허리에 두 손 얹기, 물건을 가슴 앞에 들기, 고개 숙이기.
          대사의 감정과 맞게 고른다(놀라는 말에 두 주먹, 미안한 말에 고개 숙이기, 뿌듯한 말에 허리에 손).
        · 손은 물건을 쥐거나 주먹을 쥔다. 물건을 들 때는 쥐는 곳을 쓴다(예: "holds {봉투} by its folded top", "holds {등불}
          by its handle"). 손끝이 닿는 동작은 닿는 부분까지 쓴다(예: "{B} presses a drink button on {자판기}").
        · 두 팔은 같은 일을 하거나 한 팔만 쓴다.
        · 핵심 물건은 가슴 높이에 둔다("above head"는 말풍선에 가린다). 하늘·배경의 핵심 대상(별똥별, 먼 오두막 불빛)도 컷
          가운데 높이에 두게 쓴다. 몸보다 큰 물건(로봇청소기, 상자)은 끌어안지 않고 옆에 두거나 앞에 든다.
        · 들고 다니는 물건이 나오는데 동작에 쓰지 않으면 쥔 모습을 끝에 짧게 붙인다(예: "holding {등불} by its handle at the
          side", "{가방} on the shoulder").
        피할 동작과 바꿔 쓸 동작
        · 처음과 끝이 있는 동작(내려놓기, 집어 들기, 넣기, 꺼내기, 앉는 중) → 두 모습이 겹쳐 사람이 둘. 끝난 뒤 모습으로 쓴다
          ("puts {청소기} down" 대신 "stands next to {청소기} on the floor").
        · 바닥에 엎드리기·기기·눕기·구르기 → 팔다리가 엉킨다. 무릎 꿇기, 쪼그려 앉기, 서서 내려다보기로 쓴다.
        · 손바닥 펴기(손 흔들기, 손바닥 내밀기, 손가락 펴기) → 손가락 수가 틀린다. 주먹이나 물건 쥔 동작으로.
        · 손이 물건에서 떨어진 동작(멀리 가리키기, 문 밀기, 이불 당기기) → 빈손 자세가 된다. 물건을 쥔 동작으로.
        · 두 팔이 다른 일(팔짱 끼고 가리키기, 한 손으로 들고 다른 손으로 가리키기) → 팔이 셋.
        · 작고 미묘한 동작(귓속말, 윙크, 끄덕이기, 소매 당기기)이나 감정·말만 쓰기("happy", "talks") → 그냥 서 있는 그림.
        · 문·창을 지나가는 동작 → 문 안팎에 같은 사람이 둘. 문 앞에 선 모습으로.
        · 가구 밑·뒤·안을 들여다보거나 손을 넣는 동작("looks under the sofa", 낀 물건 꺼내기) → 보는 사람과 들어가는 사람이
          둘로 그려진다. 가구 옆에 선 모습과, 밖으로 반쯤 나온 물건으로 쓴다(예: "{A} stands beside the sofa, {청소기} sticking
          out from under it").
        · look에 없는 옷·모자·안경·장신구를 만지는 동작("grips the cap brim") → 없는 물건이 하나 더 생긴다. look에 있는 것만 쓴다.
        · 동물 인물의 귀·꼬리·날개 동작("ears flopping", "tail wagging") → 귀가 넷, 꼬리가 둘.
        쓰지 않는 말
        · action에 소품 자리표는 하나만, 물건도 하나만 쓴다.
        · "with both hands", "with one hand" 같은 손 수(그림 모델이 무시한다).
        · 신발·옷 낱말(look·bottom·shoes에만 있다. 쓰면 하나 더 그려진다).
        · his·her·him·she·he 같은 대명사(몸은 the로: "puts fists on the hips"). 성별이 바뀌어 그려진다.
        가구와 발
        · 침대·소파·의자에 앉으면 다리 위치를 쓴다(예: "{A} sits on the edge of {침대}, feet on the floor", "{A} sits up in {침대}, blanket over the legs"). 신을 벗는 곳
          (침대, 이불 속, 방바닥)에서 발이 보이면 "white socks"를 쓴다("bare feet"는 쓰지 않는다).
      - place: places의 key 하나.
      - setting: 그 컷의 때·분위기 한 마디와, 더 보여야 할 배경 물건 0~1개를 그림 모델용 영어 10단어 이내로 쓴다. 장소 이름,
        인물, 표정, props에 있는 물건은 쓰지 않는다.
        · 때·하늘·빛은 장소 draw의 때와 같게, 보이는 모습으로 쓴다(예: 밤 "night, deep navy sky full of twinkling stars", 저녁 "orange sunset glow,
          long shadows", 낮 실내 "bright daylight from the window"). 대사에 나오는 별·달·노을·눈·비는 꼭 쓴다. 이야기 안에서
          시간이 흐르지 않으면 모든 컷에 같은 때를 쓴다.
        · 배경 물건은 손에 들 수 없는 큰 것(가구, 나무, 건물)만 쓴다. 컵·그릇처럼 드는 물건은 손에 함께 쥐어진다. 작은 조각
          (체스말, 구슬)은 놓인 자리와 함께만 쓰고 바닥에 흩어진 모습으로 쓰지 않는다.
        · 실내(방, 부엌, 가게, 현관, 지붕 아래) setting에는 rain·snow·raindrops를 쓰지 않는다(방 안에 비가 그려진다). 비 오는
          날 실내는 "rain streaks on the window glass, dim room". 야외에서 비를 맞으면 action에 젖은 모습을 쓴다(예: "{A} stands in
          the rain, soaked hair and clothes", 우산이 있으면 "holding {우산} by its handle over the head").
      - faces: 그 컷 주인공 하나. face는 그 순간의 표정과 몸짓 하나를 영어 4단어 이내로 쓴다(예: "wide-eyed, hands on
        cheeks", "teary smile"). 두 표정을 잇는 말("then", "turns into")은 쓰지 않는다. 컷마다 대사에 맞게 바꾼다. 성격·외모와
        안경·모자·머리, 귀·꼬리는 쓰지 않는다.
        lookAt은 그 인물이 보는 곳이다: 말을 거는 상대의 자리표({B}), 대사가 가리키는 물건의 자리표({휴대폰}), 또는 영어 한
        낱말 "viewer", "sky", "down", "away". 무엇을 가리키거나 그쪽으로 팔을 뻗는 동작이면 lookAt도 그 대상이다.
      - lines: 컷당 1~2개(대사 없는 컷은 없다), 한 문장 30자 이내. 그 컷의 순간에 해당하는 말을 [장면 대화]에서만 골라 시간
        순서대로 쓴다(1컷이 가장 먼저 한 말). 앞뒤 대화는 상황 이해에만 쓴다. 같은 말을 두 컷에 쓰지 않는다. 핵심 순간의 말은
        꼭 전 컷(전이 여러 컷이면 그중 하나)에 넣는다. 이야기를 이끄는 말(질문, 들통, 고백, 반응)을 고르고 맞장구("응", "그래")
        만으로 컷을 채우지 않는다.
        · speaker는 [장면 대화]에서 그 말을 한 화자(A, B…)다. 다른 인물 대사로 옮기지 않는다. 대사를 쓴 화자는 모두
          characters에 넣는다.
        · quote: 고른 말을 고쳐 쓰지 않고 그대로 옮긴다. 길면 앞뒤를 잘라 줄인다.
        · text: quote를 그 인물의 persona와 아래 "말투"로 다듬은 말풍선 대사(30자 이내). 뜻과 사실(누가 무엇을 했는지, 숫자,
          물건, 질문인지 대답인지)은 그대로 두고 말투와 낱말만 바꾼다. 없는 사건을 지어 넣지 않는다. 인물을 부를 때는 가명 대신
          role 호칭을 쓴다(예: A가 할머니면 "하늘아, 밥 먹었어?" → "할머니, 밥 먹었어?"). 말풍선 앞에는 role이 붙는다.
        · quote와 text에는 대사만 쓰고 "A:"나 이름 같은 화자 표시를 붙이지 않는다.
      - 말투(%CONCEPT%): %VOICE%%DIALECT%
      - text 교정: 말풍선은 그대로 인쇄된다. 말투를 입힌 뒤 한 번 더 읽고 고친다. 맞춤법(되/돼, 안/않, 데/대, 로서/로써,
        왠지/웬일, 몇일 → 며칠), 띄어쓰기(의존 명사 "할 수", "먹을 거야", "한 번", 보조 용언, 조사는 붙여 쓰기), 받아쓰기 오류
        (녹음 전사라 소리 나는 대로 잘못 적힌 말, 뜻이 안 통하는 낱말은 맥락에 맞는 말로), 어색한 표현(번역투, 꼬인 문장, 같은
        말 반복, "그 뭐냐" 같은 군말)을 바로잡는다. 일부러 쓴 말투(사투리, 테마 말투, 감탄사, ㅋㅋ)는 살린다. quote는 원문
        대조용이라 교정하지 않는다.
      - 이름: 대사(quote, text)에 대화 속 사람 이름·별명이 나오면 바꾼다. 등장 화자를 부르는 말이면 quote는 그 화자의 가명으로
        (예: "민수야" → B가 도윤이면 "도윤아"), text는 role 호칭으로, 그 밖의 사람은 "친구"로. 학교·회사 이름도 지운다. 바꾸기
        전 단어는 nameWarnings에 넣는다.
      - 실존 인물·기업: 제목, 대사(text), persona, summary에 실존 기업·브랜드·정당·단체 이름, 실존 인물(정치인, 연예인,
        운동선수, 기업 회장·총수 등) 이름, 소속과 직함을 붙여 한 사람을 가리키는 말(예: "OO그룹 회장", "OO시장")을 쓰지 않고
        누구인지 알 수 없는 일반 표현으로 바꾼다(삼성 → 대기업, 스타벅스 → 카페, OO그룹 회장 → 회장님, OO당 대표 → 한 정당
        대표). 대화에 이미 일반 표현으로 바뀌어 있으면 그대로 쓴다. 그림용 영어(action, setting, draw)에도 상표·회사 이름과
        로고를 쓰지 않는다(Starbucks cafe → a coffee shop). 바꾸기 전 단어는 nameWarnings에 넣는다.
      - 욕설과 민감한 표현은 순화한다.
      - sound: 라이브 모드에서 그 컷에 틀 배경 소리 장면을 한국어 한 줄(40자 이내)로 쓴다. "시간대·날씨 + 장소(실내/바깥),
        계속 들리는 소리와 그 결" 순서로 쓴다(예: "밤 바닷가, 잔잔하게 밀려오는 파도와 풀벌레", "비 오는 골목, 지붕을
        두드리는 빗소리", "낮 마트 안, 사람들이 낮게 웅성이고 카트가 굴러가는 소리", "아침 방 안, 창밖 빗소리"). 처음부터
        끝까지 깔리는 소리만 쓰고, 한 번 나는 소리(병 부딪힘, 문 닫힘, 알람)는 쓰지 않는다. 그 장소에 실제로 들릴 소리를 쓰고
        대사에 나온 음식·물건 이름은 쓰지 않는다. 인물 이름과 대사는 쓰지 않는다. 같은 장소가 이어지면 같은 문장을 쓴다.
      - effect: 그 컷에서 실제로 울리거나 나는 사건 소리 하나를 한국어 짧은 말(15자 이내)로 쓴다. 대사·행동·장면에 그 소리가
        나와야 한다(예: 알람이 울린다 → "휴대폰 알람 소리", 초인종을 누른다 → "초인종 소리", 문을 쾅 닫는다 → "문 쾅
        닫히는 소리", 전화가 온다 → "전화벨 소리"). 배경에 계속 깔리는 소리(빗소리, 파도, 웅성거림)는 sound에 쓰고 여기에는
        쓰지 않는다. 사건 소리가 없으면 빈 문자열 ""이고, 대부분의 컷은 ""다. 확실하지 않으면 "".
      - motion: 라이브 모드에서 그 컷 배경만 살짝 움직일지 고른다. 아래 여섯 가지 중 하나 또는 "none"이다. 그 배경이 그
        컷 setting에 실제로 쓰여 있고, 바뀌는 것이 배경뿐이고 인물·물건·구도는 그대로일 때만 고른다. 한 편에 많아야
        1~2컷이고, 나머지 컷은 모두 "none"이다. 확실하지 않으면 "none".
        · twinkle: 밤하늘 별빛·작은 불빛이 반짝인다(setting에 stars, fairy lights).
        · rain: 야외에 빗줄기가 내린다(setting에 rain). 실내 컷은 안 된다.
        · snow: 야외에 눈이 내린다(setting에 snow). 실내 컷은 안 된다.
        · flicker: 촛불·등불·네온·모닥불이 깜빡인다(setting에 candle, lantern, neon, campfire).
        · shimmer: 바다·호수·강 물결이 반짝인다(setting에 sea, lake, river).
        · petals: 꽃잎·낙엽이 흩날린다(setting에 petals, blossoms, falling leaves).
        좋은 예: 별이 가득한 밤하늘 아래 벤치에 앉은 컷 → twinkle. 비 오는 거리에 선 컷 → rain. 생일 케이크 촛불 앞 컷 →
        flicker. 벚꽃길을 걷는 컷 → petals.
        나쁜 예: 인물이 손을 흔든다 → none(인물 동작). 밤인데 setting에 별이 없다 → none. 창밖에 비가 오는 방 안 → none
        (실내). 놀라서 펄쩍 뛴다 → none. 모든 컷에 twinkle → 한두 컷만 남기고 none.
      - stir: 라이브 모드에서 그 컷 배경의 무엇이 어떻게 살짝 움직이는지 영어 한 구절(5~16낱말)로 쓴다. motion이 "none"인
        컷의 움직임 그림이 이 구절로 그려진다. 그 컷 setting에 쓴 물건 하나를 골라 그 물건 이름을 그대로 쓰고, 그 장소와
        이야기에 어울리는 움직임을 쓴다. 인물·얼굴·손은 움직이지 않고, 그림에 없는 것을 새로 생기게 하지 않는다. 바로 앞 컷과
        다른 물건을 움직인다. 나뭇잎·풀이 흔들리는 바람은 setting에 나무·풀이 있는 야외 컷에서만 쓴다.
        좋은 예: 사무실(setting에 monitors, wall clock) → "the wall clock's hands tick forward" 또는 "the code on the
        monitors scrolls up". 식당(setting에 soup pot) → "steam curls up from the soup pot". 거실(setting에 TV) → "the
        news on the TV screen changes to a new picture". 버스 안(setting에 bus window) → "the street scenery slides past
        the bus window". 횡단보도(setting에 traffic light, cars) → "the traffic light turns from red to green". 카페(setting에
        espresso machine) → "steam puffs from the espresso machine". 야외 공원(setting에 trees, clouds) → "white clouds
        drift slowly across the sky".
        나쁜 예: "she smiles"(인물). "leaves fly around the room"(실내에 없는 것). setting에 없는 물건. 앞 컷과 같은 물건.
      - ambient: 라이브 모드에서 그림 위에 화면이 얹는 작은 움직임 층이다(그림은 그대로). 아래 열일곱 가지에서 그 컷 배경에
        실제로 보이는 것만 0~2개 고른다. 대사나 장면에 하늘·날씨 사건(별똥별, 불꽃놀이, 번개)이 나오면 그 낱말을 setting에도
        쓰고 꼭 고른다. 그 낱말(괄호 안)이 그 컷 setting에 쓰여 있어야 하고, 없으면 빈 배열 []이다. motion과
        달리 여러 컷에 써도 된다. 날씨(비·눈)는 이어 쓰되, 같은 장소가 이어져도 그 컷 배경에 보이는 다른 것이 있으면 앞 컷과 다른 효과를 고른다. 확실하지 않으면 [].
        · twinkle: 별빛·전구 불빛이 반짝인다(stars, night sky, fairy lights, city lights).
        · rain: 빗줄기(rain). 야외 컷만이다. 창밖에 비가 오는 방 안은 안 된다.
        · snow: 내리는 눈(snow). 야외 컷만이다.
        · fireflies: 반딧불이가 떠다닌다(fireflies).
        · petals: 꽃잎이 흩날린다(petals, cherry blossoms).
        · leaves: 낙엽이 떨어진다(falling leaves, autumn leaves).
        · steam: 뜨거운 음식·음료·목욕물에서 김이 오른다(coffee, tea, ramen, soup, steam, bath).
        · dust: 창으로 든 햇빛 속에 먼지가 떠다닌다(sunlight, sunbeams).
        · flicker: 촛불·등불·모닥불·네온 불빛이 흔들린다(candle, lantern, campfire, neon).
        · meteor: 별똥별이 하늘을 가로질러 떨어진다(shooting star, meteor).
        · fireworks: 불꽃놀이가 터진다(fireworks).
        · lightning: 번개가 번쩍인다(lightning, thunderstorm).
        · sunbeam: 낮에 창으로 드는 빛줄기가 천천히 밝아졌다 옅어진다(window, daylight). 밤 장면은 안 된다.
        · screen: 모니터·텔레비전 화면 빛이 은은하게 깜빡인다(monitor, screen, laptop, TV).
        · bokeh: 멀리 거리 불빛·자동차 불빛이 흐릿한 빛 동그라미로 번진다(street lights, headlights, traffic, neon signs).
        · clouds: 낮 하늘에 구름이 천천히 흐른다(clouds, blue sky). 야외 낮 컷만이다.
        · bubbles: 비눗방울·수족관 거품이 떠오른다(bubbles, soap, aquarium).
        좋은 예: 별이 뜬 밤 옥상 → ["twinkle"]. 비 오는 밤 골목 → ["rain"]. 카페에서 김 나는 커피를 마신다 → ["steam"].
        햇살 드는 아침 방 → ["dust"]. 모니터가 켜진 사무실 → ["screen"]. 밤 도심 거리 → ["bokeh"]. 맑은 날 공원 → ["clouds"]. "별똥별이다!" 하며 밤하늘을 보는 컷 → ["meteor","twinkle"]. 축제 밤 불꽃놀이 → ["fireworks"]. 캠핑장 밤 모닥불 → ["flicker","twinkle"]. 벚꽃길 → ["petals"].
        나쁜 예: 밤이라는 말만 있고 setting에 별이 없다 → []. 차가운 아이스커피 → [](김이 안 난다). 창밖에 비가 오는
        방 안 → [](실내). 낮 하늘에 twinkle → []. 세 개 이상 → 두 개까지만.

      title은 20자 이내로 이 장면의 핵심을 살려 짓고(예: "사라진 아이스크림의 범인", "할머니의 비밀 레시피") 실명과 가명을
      넣지 않는다(인물은 role 호칭으로). title과 persona도 맞춤법·띄어쓰기를 지킨다. 대사·제목·persona는 대화와 같은 언어로
      쓴다.

      summary는 만화 아래에 보이는 줄거리 요약이다. 만화만 봐서는 놓치는 앞뒤 사정까지 [장면 앞 대화]·[장면 대화]·[장면 뒤
      대화]에서 있었던 일 전체를 3~4문장(250자 이내)으로 쓴다: 어떤 상황에서 시작해 무슨 일이 생겼고 어떻게 끝났는지. 인물은
      role 호칭으로만 부르고(예: "손녀가 할머니에게…") 실명·가명·화자 표시(A, B)·자리표({A})는 쓰지 않는다. 대화에 없는 일을
      지어 넣지 않고, 대사를 길게 옮기지 않는다. 대화와 같은 언어로, 맞춤법·띄어쓰기를 지켜 평서문(~다)으로 쓴다.

      mood는 이야기 전체의 분위기 하나다. 라이브 모드에서 장면에 꼭 맞는 장소 소리가 없는 컷에 이 분위기의 배경음악을 깐다.
      아래에서 하나만 고른다. 대화의 감정과 사정을 보고 고르고, 장소나 컨셉만 보고 고르지 않는다(억울하거나 힘든 처지를
      털어놓으면 SAD, 끝이 다짐이라도 이야기 대부분이 무거우면 SAD나 SOLEMN).
        · PEACEFUL 평온한 일상 · JOYFUL 기쁨·축하 · COMIC 웃긴 해프닝 · EXCITING 신나는 모험 · PARTY 떠들썩한 잔치
        · ROMANTIC 설렘·사랑 · NOSTALGIC 추억·그리움 · LONELY 쓸쓸함·외로움 · SAD 슬픔·우울·억울함 · SOLEMN 엄숙한 다짐·이별
        · TENSE 긴장·갈등 · MYSTERIOUS 수상함·비밀 · SCARY 공포 · EPIC 벅찬 승리·극복
      """;

  static final String SCRIPT_SCHEMA =
      """
      {"type":"object","required":["title","summary","mood","characters","props","places","panels","nameWarnings"],"properties":{
       "title":{"type":"string"},"summary":{"type":"string"},
       "mood":{"type":"string","enum":["PEACEFUL","JOYFUL","COMIC","EXCITING","PARTY","ROMANTIC","NOSTALGIC","LONELY","SAD","SOLEMN","TENSE","MYSTERIOUS","SCARY","EPIC"]},
       "characters":{"type":"array","minItems":1,"items":{"type":"object",
        "required":["speaker","name","role","persona","look","bottom","shoes"],"properties":{"speaker":{"type":"string"},
         "name":{"type":"string"},"role":{"type":"string"},"persona":{"type":"string"},"look":{"type":"string"},
         "bottom":{"type":"string"},"shoes":{"type":"string"}}}},
       "props":{"type":"array","maxItems":4,"items":{"type":"object","required":["key","draw"],
        "properties":{"key":{"type":"string"},"draw":{"type":"string"}}}},
       "places":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","required":["key","draw"],
        "properties":{"key":{"type":"string"},"draw":{"type":"string"}}}},
       "panels":{"type":"array","minItems":4,"maxItems":10,"items":{"type":"object",
        "required":["role","shot","action","place","setting","faces","lines","sound","motion","stir","effect","ambient"],"properties":{"role":{"type":"string","enum":["기","승","전","결"]},
         "shot":{"type":"string","enum":["wide","medium","close-up"]},
         "action":{"type":"string"},"place":{"type":"string"},"setting":{"type":"string"},
         "faces":{"type":"array","items":{"type":"object","required":["speaker","face","lookAt"],
          "properties":{"speaker":{"type":"string"},"face":{"type":"string"},"lookAt":{"type":"string"}}}},"lines":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"object",
          "required":["speaker","quote","text"],"properties":{"speaker":{"type":"string"},
           "quote":{"type":"string"},"text":{"type":"string"}}}},
         "sound":{"type":"string"},
         "effect":{"type":"string"},
         "stir":{"type":"string"},
         "motion":{"type":"string","enum":["none","twinkle","rain","snow","flicker","shimmer","petals"]},
         "ambient":{"type":"array","maxItems":2,"items":{"type":"string",
          "enum":["twinkle","rain","snow","fireflies","petals","leaves","steam","dust","flicker","meteor","fireworks","lightning","sunbeam","screen","bokeh","clouds","bubbles"]}}}}},
       "nameWarnings":{"type":"array","items":{"type":"string"}}}}
      """;
}
