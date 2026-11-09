package net.java21.fourcut.conversation.scenario;

/**
 * LLM 프롬프트와 출력 JSON 스키마. 바꾸면 VERSION을 올린다 (단계 기록 model_info에 남는다).
 *
 * <p>qwen3 thinking은 워커가 끈다(think=false). 그래서 "속으로 여러 안을 만들어 고르라" 같은 지시는 두지 않는다. 맞춤법·실명 검수도 콘티 호출 한
 * 번에 함께 시킨다(LLM 호출 1회 절약).
 */
final class Prompts {

  static final String VERSION = "2026-11-09.11";

  private Prompts() {}

  /** 장면 후보 JSON(후보 3개, 핵심 순간, 항목 점수, 이름)은 짧다. 넘치면 잘린 JSON이 되어 재시도한다. */
  static final int EXTRACT_MAX_TOKENS = 1100;

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
      신호가 나온 줄을 중심으로, 그 일이 시작된 줄부터 반응이 가라앉는 줄까지를 한 장면으로 잡는다.

      고르지 않는 것
      - 인사, 안부, 자리 잡기, 늦은 사과, 메뉴 고르기·주문·계산 같은 진행 대화.
      - 반응이나 결말 없이 정보만 오가는 잡담, 한 사람이 혼자 길게 설명만 하는 구간.
      - 욕설·민감한 주제(건강, 정치, 성, 혐오, 개인정보)가 중심인 장면.

      출력
      - 최대 3개, 서로 겹치지 않게, 가장 특별한 장면부터. 특별한 순간이 없으면 빈 목록이 낫다.
      - kind: FUNNY(즐거운 대화: 웃음, 반전, 엉뚱한 말, 티격태격), TOUCHING(남기고 싶은 대화: 진심, 고마움, 응원, 위로),
        IMPORTANT(중요한 내용: 결정, 약속, 소식, 계획), MEMORY(추억: 함께 겪은 지난 일을 떠올리는 이야기),
        CORE(위에 맞지 않지만 이 대화가 가장 오래·깊게 다룬 주제).
      - from, to: 장면 첫 줄과 마지막 줄 앞의 [분:초] 표시를 그대로 옮긴다(예: "04:11"). 장면 길이는 30초~3분.
      - peak: 이 장면을 특별하게 만드는 한 마디. [from, to] 안에 있는 말을 고쳐 쓰지 말고 짧게 옮긴다(40자 이내).
      - summary 한 줄: 무슨 상황에서 어떤 일이 생겨 어떻게 됐는지. 그 일이 실제로 from~to 줄 안에 나와야 한다.
        reason 한 줄: 대화한 사람들에게 왜 특별한지. 사람은 이름 대신 화자 표시(A, B…)로 쓴다.
      - 항목 점수 1~5. 후하게 주지 않는다. 5는 이 대화에서 손꼽히는 장면에만 준다.
        story 이야기성: 상황 → 사건 → 반응 → 결말이 이 구간 안에 다 나오는가.
        visual 그림: 행동, 장소, 물건이 있어 그림으로 보여 줄 수 있는가.
        relatable 공감: 대화한 사람들이 보고 "맞아, 이랬지" 할 장면인가.
        memorable 간직: 몇 년 뒤에도 꺼내 보고 싶은가. 웃음이 크게 터졌거나 마음이 움직였으면 높고, 평범한 잡담은 1~2.
      - intro: 인사, 안부, 자리 잡기, 늦은 사과처럼 본론 전 도입부면 true.
      - concept: 이 장면에 어울리는 그림 컨셉 하나. DAILY(일상 웹툰: 친구·회사·일상 이야기), ANIMALS(동물 친구들: 귀엽고 가벼운 다툼·먹는 이야기), WATERCOLOR(수채화
        애니메이션: 추억, 여행, 계절, 따뜻한 감정), PICTURE_BOOK(어린이 그림책: 가족, 아이, 소박한 하루),
        NEWSPAPER(흑백 신문 만화: 풍자, 회사·사회 이야기, 어이없는 반전), JOSEON(조선시대 사람들: 옛날이야기처럼
        풀면 재미있는 가족·이웃·장터 이야기), WUXIA(무협: 내기, 승부, 비밀, 사소한 일을 크게 부풀린 대결).
      - names: 대화에서 사람 이름(실명·별명)으로 쓰인 단어만 최대 6개. 조사·호격을 뺀 형태로 쓴다(민지야 → 민지,
        준호는 → 준호). 직함·호칭(팀장, 사장님, 언니), 음식, 물건, 장소, 가게 이름은 넣지 않는다. 없으면 빈 목록.
      - 대화와 같은 언어로 쓴다.
      """;

  static final String EXTRACT_SCHEMA =
      """
      {"type":"object","required":["candidates","names"],"properties":{
       "candidates":{"type":"array","maxItems":3,
       "items":{"type":"object",
        "required":["from","to","peak","kind","summary","reason","story","visual","relatable","memorable","intro","concept"],
        "properties":{"from":{"type":"string","pattern":"^[0-9]{2,3}:[0-9]{2}$"},"to":{"type":"string","pattern":"^[0-9]{2,3}:[0-9]{2}$"},
         "peak":{"type":"string"},
         "kind":{"type":"string","enum":["FUNNY","TOUCHING","IMPORTANT","MEMORY","CORE"]},
         "summary":{"type":"string"},"reason":{"type":"string"},
         "story":{"type":"integer","minimum":1,"maximum":5},"visual":{"type":"integer","minimum":1,"maximum":5},
         "relatable":{"type":"integer","minimum":1,"maximum":5},"memorable":{"type":"integer","minimum":1,"maximum":5},
         "intro":{"type":"boolean"},
         "concept":{"type":"string","enum":["DAILY","ANIMALS","WATERCOLOR","PICTURE_BOOK","NEWSPAPER","JOSEON","WUXIA"]}}}},
       "names":{"type":"array","maxItems":6,"items":{"type":"string"}}}}
      """;

  /** 장면 종류마다 콘티의 흐름과 분위기. 콘티 입력의 "만화 분위기"로 넣는다. */
  static String tone(String kind) {
    return switch (kind == null ? "" : kind) {
      case "FUNNY" -> "웃음. 기·승에서 복선을 깔고, 전에서 웃음 포인트(반전·들통·엉뚱한 말)가 터지고, 결은 모두의 반응이나 한 방 더 웃긴 마무리.";
      case "TOUCHING" -> "진심. 전에서 진심이 담긴 말이 나오는 순간을 크게 보여 주고, 결은 서로의 표정과 따뜻한 여운.";
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

      인물 (characters, 화자 A, B…마다 하나)
      - name: 입력의 "인물 가명"을 그대로 쓴다. 가명은 만화 아래 등장인물 소개에만 나오고 대사·제목에는 쓰지 않는다.
      - role: 만화 속에서 이 인물을 부르는 호칭 한 낱말(8자 이내). 대화 내용과 서로 부르는 말로 관계를 짐작해 정한다(예: 할머니,
        할아버지, 엄마, 아빠, 손녀, 손자, 아들, 딸, 언니, 오빠, 형, 동생, 친구, 선배, 후배, 팀장, 동료). 알 수 없으면 "친구".
        인물끼리 겹치지 않게 한다(친구가 둘이면 "친구"와 "단짝"처럼).
      - persona: 이 장면에서 드러난 성격을 "%CONCEPT%" 세계의 인물로 한 줄(예: 먹는 데 진심인 친구). 대사 말투는 이
        persona를 따르고, 한 인물은 모든 컷 내내 같은 말투를 쓴다.
      - look: %LOOK%
      - bottom: 아래옷을 그림용 영어 4단어 이내로 색과 함께 쓴다(예: "blue jeans", "long navy skirt", "white trousers").
        한 벌 옷(dress, robe, overalls)이면 그 아래 보이는 것을 쓰고, 바지를 입지 않은 동물은 "bare fur legs".
      - shoes: 신발을 그림용 영어 3단어 이내로 색과 함께 쓴다(예: "white sneakers", "black cloth shoes"). 맨발이면 "bare feet".
      - 동물 인물이 둘 이상이면 종·털색·몸집이 한눈에 다르게 고른다(회색 토끼와 회색 고양이처럼 비슷하면 한 인물로 섞여
        그려진다). 동물 look에는 털색을 쓴다(예: "brown bear", "white rabbit").
      - look의 옷은 몸통을 덮는 윗옷(셔츠, 스웨터, 조끼, 도포, 저고리) 하나를 색과 함께 꼭 쓴다. 모자·목도리·안경·수염만
        쓰면 컷마다 윗옷이 바뀌어 그려진다.
      - look·bottom·shoes는 이 인물이 모든 컷에서 입는 한 벌이다. 컷마다 옷을 바꾸지 않는다. 가운·제복·승복 같은 직업 옷은
        이야기에 그 일을 하는 장면이 나올 때만 쓴다.

      소품 (props, 0~4개, 한 번만 정한다)
      - 이야기의 핵심 물건과 중심 동물(반려동물 등). key는 짧은 한국어 이름(예: 줄넘기, 연, 강아지).
        draw는 그림용 영어 8단어 이내로 재질·색·크기·종까지 쓴다(예: "red plastic jump rope with wooden handles",
        "small yellow diamond-shaped paper kite", "small old brown-and-white dog"). 반려동물 소품은 "tiny"로 시작하고 "on four legs, no
        clothes"를 붙여 옷 입은 인물과 섞이지 않게 쓴다. 동물 인물과 반려동물이 한 컷에 나오면 반려동물은 그 인물 발치에 둔다
        (예: "{강아지} sits at {A}'s feet"). 비슷한 다른 물건(연 → 풍선, 노란 연 →
        다른 색 연)으로 바뀌지 않게 구체적으로 쓴다.
      - 인물이 여러 컷에 들고 다니거나 몸에 지니는 물건(가방, 지팡이, 책, 휴대폰)도 props에 넣는다. props에 없는 물건은 컷마다
        다른 물건(배낭 → 손가방 → 바구니)으로 그려진다.
      - 소품 하나는 물건 한 개다. 같은 물건 여러 개(봉지 두 개, 휴대폰 두 대)나 작은 물건이 여럿 든 것("가득 든 햄버거",
        "full of dumplings")으로 쓰지 않는다. 그림 모델이 물건을 하나 더 그리거나 작은 물건을 옷·머리에 따로 붙인다.
        draw에는 바뀌지 않는 생김새만 쓰고, 화면에 보이는 것·열림·켜짐 같은 상태는 그 컷 action에만 한 번 쓴다. 두 곳에 다르게
        쓰면 같은 물건이 두 개로 그려진다.
      - 모양이 헷갈리기 쉬운 물건(판 놀이, 기계, 음식)은 그림 모델이 알아보게 생김새를 쓴다: 전체 모양, 겉면에 보이는 것,
        작은 조각이 어디에 어떻게 놓이는지(예: "flat square chessboard of black and white squares, a few small chess
        pieces standing on it", "one tall red vending machine with rows of drink cans behind glass").
        생김새가 비슷한 다른 물건이 있으면(망원경 ↔ 쌍안경, 외발자전거 ↔ 자전거) 둘을 가르는 부분의 개수를 쓴다(예: "single
        long brass telescope tube with one eyepiece on a tripod"). 개수가 없으면 더 흔한 쪽 물건으로 그려진다.
        작은 조각을 집는 동작은 크기를 함께 쓴다(예: "{A} lifts one tiny white chess piece with two fingers").
      - 그림 모델은 글자를 제대로 못 쓰고 가짜 글자를 그린다. 글자가 보이는 물건(간판, 팻말, 문자 메시지, 책 표지)은 글자 없이
        모양과 색으로 쓴다(예: "blank wooden shop sign", "phone with glowing green screen"). 병풍·족자·액자·칠판·화면처럼
        글씨나 그림이 들어가기 쉬운 면은 "plain"을 붙여 무늬 없이 쓰고, 화면에 보여야 할 것은 글자 대신 그림 하나로 쓴다
        (예: "screen showing a big burger picture").
      - 들고 다니는 물건이 나오는 컷인데 action이 그 물건을 쓰지 않으면, 쥔 모습을 action 끝에 짧게 붙인다(예: "holding
        {등불} by its handle at her side", "{가방} on his shoulder"). 쥔 모습이 없으면 팔을 쭉 뻗어 매달거나 엉뚱하게 든다.
      - 인물마다 들고 다니는 물건은 하나까지만 두고, 이야기에 꼭 필요할 때만 look에 넣는다. 길쭉한 물건(칼, 검, 지팡이, 막대,
        부채)은 편 전체에서 한 인물에게만 준다. 비슷한 물건을 두 인물에게 나눠 주면 서로 바뀌어 그려진다.
      - draw·action·setting에 그림 모델이 글자로 써 넣는 낱말(order, menu, sale, exit, open, number, sign 위 문구, 가게
        이름)을 쓰지 않는다. "order kiosk" 대신 "touchscreen kiosk"처럼 생김새로 쓴다.

      장소 (places, 1~3개, 한 번만 정한다)
      - key는 짧은 한국어 이름(예: 놀이터, 세탁실). draw는 그림용 영어 14단어 이내로 장소 이름을 먼저 쓰고, 한눈에 그 장소인 걸
        알아보게 하는 특징 2~3개를 색·모양과 함께 붙인다(예: "small playground, yellow slide, blue swings, sandbox",
        "school library, tall wooden bookshelves, long reading tables"). 특징은 크고 눈에 띄는 것만
        고르고, 자잘한 물건을 늘어놓지 않는다. 이 말이 모든 컷에 같은 말로 들어가 컷마다 같은 장소로 보인다.
        대화 내용과 시대(%CONCEPT%)에 맞게 정한다.
      - 이야기 속에서 실제로 옮겨 갈 때만 장소를 하나 더 둔다.

      자리표: 인물은 {A}, {B}, 소품은 {연}처럼 key를 중괄호로 감싸 쓴다. action·setting·lookAt에서 인물과 소품을 영어로 다시
      쓰지 않고 자리표만 쓴다. 자리표는 모든 컷에 같은 외모·draw 말로 바뀌어 들어간다.

      컷 수 (panels 4~10개)
      - 기본은 4컷이다. 4컷에 담으면 이야기의 중요한 순간이 빠질 때만 늘린다. 컷이 늘면 그리는 시간도 그만큼 늘어나므로
        필요한 만큼만 쓴다.
      - 늘리는 경우: 사건이 여러 단계로 이어진다(준비 → 문제 → 수습 → 또 문제), 오해·반전이 두 번 이상 있다, 여러 인물이
        차례로 반응해야 재미가 산다, 주고받은 말 자체가 웃음이라 줄이면 맛이 사라진다.
      - 4컷으로 두는 경우: 한 번의 반전이나 한마디가 핵심이다, 순간이 짧다, [장면 대화]에 쓸 만한 말이 적다.
      - 컷마다 서로 다른 대사가 1~2개 있어야 하므로, [장면 대화]에서 이야기를 이끄는 말이 컷 수보다 적으면 컷을 늘리지 않는다.
        맞장구나 같은 말 되풀이로 컷을 채우지 않는다.
      - 대략 짧은 일화는 4컷, 사건이 한두 번 더 이어지면 6컷, 여러 단계로 길게 이어지면 8컷, 10컷은 정말 긴 이야기에만 쓴다.

      컷 (panels, role 순서 "기","승","전","결")
      - 대화한 사람들이 간직할 기념 만화다. 먼저 "장면 요약"의 이야기를 정한 컷 수만큼의 순간으로 나눈다. 기: 상황(누가 어디서
        무엇을) / 승: 사건이 생김, 전으로 가는 복선 / 전: "핵심 순간"의 말이 나오는 컷, 반전·감정이 가장 큰 순간 /
        결: 결과·반응·여운. 흐름과 분위기는 "만화 분위기"를 따른다. 컷을 차례로 보면 요약한 이야기가 그대로 읽혀야 한다.
      - role: 첫 컷은 "기", 마지막 컷은 "결"이고 결은 한 컷이다. 기·승·전·결이 모두 나오고 순서는 거꾸로 가지 않는다.
        4컷이면 기·승·전·결 한 컷씩, 더 많으면 승과 전을 여러 컷으로 늘린다(기는 두 컷까지). 예: 6컷 기·승·승·전·전·결,
        8컷 기·승·승·승·전·전·전·결.
      - 모든 컷은 서로 다른 순간이다. 같은 행동·같은 구도를 반복하지 않는다.
      - shot: wide, medium, close-up 중 하나. wide는 온몸과 장소가 보이는 컷, medium은 허리 위, close-up은 가슴 위 얼굴이
        크게 보이는 컷이다. 기는 보통 wide, 전은 보통 close-up이고, wide는 전체 컷의 절반을 넘지 않는다. close-up은 한
        사람만 나오는 컷에 쓴다. 같은 구도가 두 컷 넘게 이어지지 않게 섞는다.
        shot은 action과 맞아야 그림에 그대로 나온다. 걷기·달리기·뛰기·앉기·무릎 꿇기·웅크리기·눕기처럼 다리가 보여야 하는
        동작은 wide에만 쓴다. medium·close-up 컷의 action은 손·팔·얼굴만 쓰는 동작이다(물건 들어 올리기,
        내밀기, 버튼 누르기, 볼 감싸기).
        침대·소파·의자에 앉거나 눕는 동작은 다리가 어디 있는지 쓴다(예: "{A} sits on the edge of {침대}, feet on the floor",
        "{A} sits up in {침대}, blanket over her legs"). 다리 위치가 없으면 다리가 가구 속에 묻혀 그려진다.
      - 컷에 나오는 사람: 한 컷은 그 컷의 주인공 한 명을 기본으로 그린다. 두 사람이 몸으로 함께 하는 일(물건 건네기, 같은
        물건 함께 보기)이 장면의 핵심일 때만 두 명을 넣는다. 두 명 컷은 편 전체에서 한 컷까지이고
        shot은 wide로 둔다. 세 명 이상은 넣지 않는다.
        한 컷에 여러 명이 나오면 그림 모델이 같은 사람을 둘로 그리거나 두 사람의 옷·얼굴을 섞는다.
        두 명 컷은 두 사람을 왼쪽과 오른쪽에 떨어뜨려 세우고, 몸이 겹치거나 손이 맞닿는 동작(안기, 손잡기, 악수, 함께 쥐기,
        귓속말)을 쓰지 않는다. 두 명 컷은 한 사람만 움직인다. 움직이는 사람은 물건을 하나까지만 자기 가슴 앞에 쥐고, 다른 사람은 떨어져
        서서 빈손으로 팔을 내린 채 표정으로만 반응한다. 두 사람이 같은 동작을 따라 하게 쓰지 않는다(물건까지 따라 들어 물건이
        둘로 그려진다). 움직이는 사람을 먼저 쓰고, 반응하는 사람은 "stands apart, arms down"으로 쓴다 (예: "{A} and {B} stand side by side looking up at {연}").
        두 명 컷에서는 팔을 옆으로 벌리거나 상대 쪽으로 뻗지 않는다(두 사람 팔이 하나로 합쳐진다).
        두 명 컷에서 물건을 상대 쪽으로 내밀지("holds out X toward {B}") 않는다. 상대가 손을 뻗어 함께 쥔 모습으로 그려진다.
        물건을 든 사람은 자기 가슴 앞에 들고, 다른 사람은 떨어져서 본다. 한 물건(우산, 쟁반, 상자)을 두 사람이 함께
        쥐거나 함께 쓰지 않는다. 팔이 합쳐지거나 한 사람 몸이 하나 더 그려진다.
        두 사람이 탁자·판·책상을 사이에 두고 마주 앉는 대칭 구도도 쓰지 않는다(같은 사람이 양쪽에 둘로 그려진다). 판 놀이나
        식사처럼 마주 앉는 장면은 한 사람만 그리거나, 두 사람을 같은 쪽에 나란히 두고 같은 방향을 보게 쓴다.
      - action: 그림 모델용 영어 14단어 이내. 그 컷에서 눈에 보이는 몸동작 하나를 "누가 + 동작 + 무엇을" 순서로 쓴다
        (예: "{A} jumps over {줄넘기} swinging it", "{B} runs holding {연} string up high").
        물건을 쓰는 동작은 단순한 동사로 무엇을 어떻게 하는지 쓰고("holds up", "pours", "presses"), "with both hands",
        "with one hand"처럼 손 수를 정하는 말은 쓰지 않는다(그림 모델이 자주 무시한다). 버튼·작은 조각처럼 손끝이 닿는 곳만
        물건의 어느 부분에 무엇을 하는지, 그 부분에 무엇이 보이는지까지 쓴다
        (예: "{B} presses a drink button on {자판기}", "{A} pours water from {주전자} into
        a cup", "{A} holds {휴대폰} up to {B}, screen showing a photo").
        이야기의 핵심 물건은 두 컷 이상에 나온다. 핵심 물건은 가슴 높이에서 들거나 내밀게 쓰고 "above head", "high up"으로
        들지 않는다. 컷 위쪽은 말풍선 자리라 물건이 가려진다. 하늘이나 배경에 있는 핵심 대상(토성, 별똥별, 무지개, 먼 오두막 불빛)도
        컷 가운데 높이에 두게 쓴다(예: "ringed planet low above the mountains, in the middle of the frame"). 몸보다 큰 물건(로봇청소기, 상자, 베개)은 끌어안지 않고 옆에 두거나
        두 손으로 앞에 들게 쓴다. 끌어안으면 몸과 물건이 붙어 그려진다. "happy", "smiles", "talks", "listens", "shares news"처럼 감정이나 말하는
        모습만 쓰지 않는다. 동작은 손·팔·몸이 무엇을 하는지 보이게 쓴다.
        동작은 얼굴을 가려도 실루엣만으로 읽히는 큰 동작으로 고른다. 소매 당기기, 귓속말, 손가락 개수 세기, 윙크, 고개 끄덕이기
        처럼 작고 미묘한 동작은 그림에서 그냥 서 있는 모습으로 바뀌므로, 같은 뜻의 큰 동작(두 팔을 앞으로 뻗기, 두 손을 번쩍
        들기, 물건을 머리 위로 들기)으로 바꿔 쓴다. 한 동작에 물건은 하나만 쓴다.
        손이 물건에서 떨어진 동작(멀리 있는 것 가리키기, 문 밀기, 이불 당기기, 엄지로 뒤 가리키기)은 그림에서 빈손 자세로
        바뀌므로, 손에 물건을 쥐고 있는 동작(들어 올리기, 내밀기, 받치기)이나 버튼처럼 손끝이 닿는
        동작으로 쓴다.
        두 팔이 서로 다른 일을 하는 동작(팔짱 끼고 가리키기, 한 손으로 들고 다른 손으로 가리키기)은 팔이 셋으로 그려지므로,
        한 팔만 쓰거나 두 팔이 같은 일을 하는 동작으로 쓴다.
        그 컷 lines의 speaker는 모두 그 컷에 나오는 인물이다. 대사를 고를 때 컷에 나오는 사람의 말을 고르고, 두 사람의 말을
        주고받는 순간은 두 명 컷 수가 남아 있을 때만 두 명 컷으로 두고, 아니면 그 컷 주인공 한 사람의 말만 고른다(예: "{A} holds up {연} at chest, {B} stands apart looking at it", "{A} and {B} stand apart, both looking
        up at {자판기}"). 한 컷에 세 화자가 말하게 되면 대사를 두 화자로 줄인다.
      - place: places의 key 하나. 장소는 key로만 고른다.
      - setting: 그 컷의 때와 분위기를 보여 주는 배경 한 마디와, 더 보여야 할 배경 물건 0~1개를 그림 모델용 영어 10단어
        이내로 쓴다. 장소 이름과 인물·표정은 쓰지 않는다. 때·하늘·날씨·빛은 눈에 보이는 모습으로 쓴다(예: 밤 "night, deep
        navy sky full of twinkling stars", 저녁 "orange sunset glow, long shadows", 낮 실내 "bright daylight from the
        window", 야외 비 "heavy rain falling on the street"). 대사나 장면에 나오는 별·달·노을·눈·비는 꼭 쓴다.
        체스말·구슬·동전처럼 작은 조각은 그것이 놓이는 자리(판 위, 그릇 안)와 함께만 쓰고, 바닥에 흩어진 모습("scattered
        stones")으로 쓰지 않는다. props에 있는 물건을 setting에 다시 쓰지 않는다.
        setting의 배경 물건은 손에 들 수 없는 큰 것(가구, 나무, 기계, 건물)만 쓴다. 컵·그릇·찜기처럼 손에 드는 물건이
        배경에 있으면 action의 물건과 함께 쥐어져 손이 셋으로 그려진다. 컷에 나오지 않는 인물은 setting에 쓰지 않는다.
        실내 컷(place가 방·부엌·가게·현관처럼 지붕 아래인 컷)의 setting에는 rain·snow·raindrops 낱말을 아예 쓰지 않는다.
        이 낱말이 있으면 방 안에 빗줄기가 그려진다. 비 오는 날 실내는 "dim gray light from the window"처럼 빛으로만 쓴다. 이야기 안에서 시간이
        흐르지 않으면 모든 컷에 같은 때(낮·저녁·밤)를 쓴다.
      - faces: 그 컷 action에 나오는 인물마다 하나. face는 그 순간의 표정과 몸짓을 그림 모델용 영어 4단어 이내로
        쓴다(예: "wide-eyed, hands on cheeks", "teary smile"). 대사와 상황에 맞게 컷마다 바꾸고, 모든 컷을 같은 표정으로
        두지 않는다. 성격·외모·이름과 안경·모자·머리 같은 몸에 지닌 것은 쓰지 않는다(다른 인물에게 옮겨 그려진다). lookAt은 그 인물이 보는 곳이다: 말을 거는 상대의 자리표({B}), 대사가 가리키는
        물건의 자리표({휴대폰}), 또는 "viewer"·"sky"·"down" 같은 영어 한 낱말. 말하는 사람과 듣는 사람이 엉뚱한 곳을 보지 않게 한다.
        action이 무엇을 가리키거나 그쪽으로 팔을 뻗거나 바라보는 동작이면 lookAt도 그 대상으로 쓴다. 두 명 컷에서 두 사람
        lookAt을 서로({A}↔{B})로 쓰면 두 사람이 마주 보며 서로에게 팔을 뻗게 그려지므로, 두 명 컷은 두 사람 모두 같은 대상을 보게
        쓴다.
      - lines: 컷당 1~2개(대사 없는 컷은 만들지 않는다), 한 문장 30자 이내. 그 컷의 순간에 해당하는 말을 [장면 대화]에서만 골라 시간 순서대로 쓴다(1컷 대사가 가장 먼저 한
        말, 마지막 컷이 가장 나중). 앞뒤 대화는 상황 이해에만 쓴다. 대화의 말을 고쳐 쓰지 말고 그대로 옮기되,
        길면 앞뒤를 잘라 줄인다. 같은 말을 두 컷에 쓰지 않는다. 핵심 순간의 말은 꼭 전 컷(전이 여러 컷이면 그중 하나) 대사에 넣는다.
        이야기를 이끄는 말(질문, 들통, 고백, 반응)을 고르고 "응", "그래" 같은 맞장구만으로 컷을 채우지 않는다.
        speaker는 [장면 대화]에서 그 말을 한 화자(A, B…)를 그대로 쓴다. 한 사람의 말을 다른 인물 대사로 옮기지 않는다.
        대사를 쓴 화자는 말이 적어도 모두 characters에 넣는다.
        quote에는 위 규칙대로 [장면 대화]에서 고른 말을 고쳐 쓰지 않고 그대로 옮긴다.
        text에는 quote를 그 인물의 persona와 아래 "말투"로 다듬은 말풍선 대사를 쓴다(30자 이내). 뜻과 사실(누가
        무엇을 했는지, 숫자, 물건, 질문인지 대답인지)은 quote 그대로 두고 말투와 낱말만 바꾼다. quote에 없는
        사건·정보를 지어 넣지 않는다.
        quote와 text에는 대사만 쓰고 "A:"나 이름 같은 화자 표시를 붙이지 않는다.
        text에서 인물을 부르거나 가리킬 때는 가명 대신 그 인물의 role 호칭을 쓴다(예: "하늘아, 밥 먹었어?" → A가 할머니면
        "할머니, 밥 먹었어?", "하늘이가 그랬어" → "손녀가 그랬어"). 말풍선 앞에는 role이 붙는다.
      - 말투(%CONCEPT%): %VOICE%%DIALECT%
      - text 교정: 말풍선은 그대로 인쇄되므로 text는 반드시 바른 글이어야 한다. 말투를 입힌 뒤 한 번 더 읽고 고친다.
        맞춤법(되/돼, 안/않, 데/대, 로서/로써, 왠지/웬일, 몇일 → 며칠), 띄어쓰기(의존 명사 "할 수", "먹을 거야",
        "한 번", 보조 용언, 조사는 붙여 쓰기), 받아쓰기 오류(녹음 전사라 소리 나는 대로 잘못 적힌 말, 뜻이 안 통하는
        낱말은 앞뒤 맥락에 맞는 말로), 어색하거나 이상한 표현(번역투, 문장 성분이 빠지거나 꼬인 문장, 같은 말 반복,
        "그 뭐냐", "막 이렇게" 같은 군말)을 자연스러운 한국어로 바로잡는다. 일부러 쓴 말투(사투리, 테마 말투, 감탄사,
        ㅋㅋ)는 틀린 것이 아니니 살린다. quote는 원문 대조용이라 교정하지 않고 그대로 둔다.
      - 대사(quote, text)에 대화 속 사람 이름·별명이 나오면 바꾼다: 등장 화자를 부르는 말이면 quote는 그 화자의 가명으로(예: "민수야" → B가 도윤이면
        "도윤아"), text는 그 화자의 role 호칭으로, 그 밖의 사람은 "친구"로. 학교·회사 이름도 지운다. 바꾸기 전 단어는 nameWarnings에 넣는다.
      - 욕설과 민감한 표현은 순화한다.

      title은 20자 이내로 이 장면의 핵심을 살려 짓고(예: "사라진 아이스크림의 범인", "할머니의 비밀 레시피") 실명과 가명을 넣지 않는다
      (인물은 role 호칭으로). title과 persona도
      맞춤법·띄어쓰기를 지킨다. 대사·제목·persona는 대화와 같은 언어로 쓴다.
      """;

  static final String SCRIPT_SCHEMA =
      """
      {"type":"object","required":["title","characters","props","places","panels","nameWarnings"],"properties":{
       "title":{"type":"string"},
       "characters":{"type":"array","minItems":1,"items":{"type":"object",
        "required":["speaker","name","role","persona","look","bottom","shoes"],"properties":{"speaker":{"type":"string"},
         "name":{"type":"string"},"role":{"type":"string"},"persona":{"type":"string"},"look":{"type":"string"},
         "bottom":{"type":"string"},"shoes":{"type":"string"}}}},
       "props":{"type":"array","maxItems":4,"items":{"type":"object","required":["key","draw"],
        "properties":{"key":{"type":"string"},"draw":{"type":"string"}}}},
       "places":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"object","required":["key","draw"],
        "properties":{"key":{"type":"string"},"draw":{"type":"string"}}}},
       "panels":{"type":"array","minItems":4,"maxItems":10,"items":{"type":"object",
        "required":["role","shot","action","place","setting","faces","lines"],"properties":{"role":{"type":"string","enum":["기","승","전","결"]},
         "shot":{"type":"string","enum":["wide","medium","close-up"]},
         "action":{"type":"string"},"place":{"type":"string"},"setting":{"type":"string"},
         "faces":{"type":"array","items":{"type":"object","required":["speaker","face","lookAt"],
          "properties":{"speaker":{"type":"string"},"face":{"type":"string"},"lookAt":{"type":"string"}}}},"lines":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"object",
          "required":["speaker","quote","text"],"properties":{"speaker":{"type":"string"},
           "quote":{"type":"string"},"text":{"type":"string"}}}}}}},
       "nameWarnings":{"type":"array","items":{"type":"string"}}}}
      """;
}
