package net.java21.fourcut.conversation.scenario;

/** LLM 프롬프트와 출력 JSON 스키마. 바꾸면 VERSION을 올린다 (단계 기록 model_info에 남는다). */
final class Prompts {

  static final String VERSION = "2026-10-31.1";

  private Prompts() {}

  static final String EXTRACT_SYSTEM =
      """
      너는 친구들의 대화 녹음 전사를 읽고 4컷 만화로 만들 만한 장면을 찾는 편집자다.
      - 장면 종류: FUNNY(모두가 웃은 웃음 포인트, 반전, 엉뚱한 말) 또는 IMPORTANT(약속, 결정, 소식 같은 중요한 일).
      - 이 구간에서 최대 2개만 고른다. 만화로 만들 만한 장면이 없으면 빈 목록을 낸다.
      - 욕설·민감한 내용(건강, 정치, 성, 혐오, 개인정보)이 중심인 장면은 고르지 않는다.
      - start, end는 대화 시작부터의 초(전사의 [분:초]를 초로 바꾼 값)다. 장면은 30초~3분 길이로 잡는다.
      - summary는 한 줄 요약, reason은 고른 이유. 사람 이름 대신 화자 가명(A, B…)을 쓴다.
      - score는 0~1, 만화로 만들었을 때 재미·의미가 클수록 높다.
      - 요약과 이유는 대화와 같은 언어로 쓴다.
      """;

  static final String EXTRACT_SCHEMA =
      """
      {"type":"object","required":["candidates"],"properties":{"candidates":{"type":"array","maxItems":2,
       "items":{"type":"object","required":["kind","start","end","summary","reason","score"],
        "properties":{"kind":{"type":"string","enum":["FUNNY","IMPORTANT"]},"start":{"type":"number"},
         "end":{"type":"number"},"summary":{"type":"string"},"reason":{"type":"string"},
         "score":{"type":"number","minimum":0,"maximum":1}}}}}}
      """;

  static final String SCRIPT_SYSTEM =
      """
      너는 대화 장면을 기-승-전-결 4컷 만화 콘티로 바꾸는 작가다.
      - 먼저 속으로 콘티 3안을 만들고, 재미·장면 충실도·대사 자연스러움으로 채점해 가장 좋은 1안만 낸다.
      - 컷은 정확히 4개, role은 순서대로 "기", "승", "전", "결".
      - scene은 그림 모델(SDXL)이 읽을 영어 구절로 쓴다. 인물(person A…)의 행동과 표정, 장소 순으로 20단어 이내.
        예: "person A laughing and pointing at an empty plate, person B shocked, kitchen table"
      - lines는 컷당 0~2개, 한 말풍선은 한 문장(최대 30자)이다. 실제 대화의 말투를 살리되 짧게 다듬는다.
      - 화자는 전사의 가명(A, B…)만 쓴다. 실명, 별명, 학교·회사 이름은 쓰지 않는다.
      - characters에는 나오는 화자마다 그림용 외모를 영어 8단어 이내로 정한다(머리, 옷, 색). 예: "short black hair, yellow hoodie".
        실제 외모를 추측하지 않는다.
      - title은 20자 이내. 대사와 제목은 대화와 같은 언어로 쓴다.
      """;

  static final String SCRIPT_SCHEMA =
      """
      {"type":"object","required":["title","characters","panels"],"properties":{
       "title":{"type":"string"},
       "characters":{"type":"array","minItems":1,"items":{"type":"object","required":["speaker","look"],
        "properties":{"speaker":{"type":"string"},"look":{"type":"string"}}}},
       "panels":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"object",
        "required":["role","scene","lines"],"properties":{"role":{"type":"string","enum":["기","승","전","결"]},
         "scene":{"type":"string"},"lines":{"type":"array","maxItems":2,"items":{"type":"object",
          "required":["speaker","text"],"properties":{"speaker":{"type":"string"},"text":{"type":"string"}}}}}}}}}
      """;

  static final String REVIEW_SYSTEM =
      """
      너는 만화 대사 교정자다. 받은 4컷 콘티를 검수해 고친 콘티를 낸다.
      - 맞춤법, 띄어쓰기, 문장 자연스러움을 고친다. 뜻과 말투는 바꾸지 않는다.
      - 말풍선은 컷당 2개 이내, 한 말풍선은 한 문장으로 줄인다.
      - 실명이나 실명으로 보이는 말(사람 이름, 별명)이 남아 있으면 가명(A, B…)이나 "친구" 같은 말로 바꾸고,
        바꾸기 전 단어를 nameWarnings에 넣는다. 없으면 빈 목록.
      - 욕설과 민감한 표현은 순화한다.
      - 구조(컷 4개, role, speaker)는 그대로 둔다. 대화와 같은 언어로 쓴다.
      """;

  static final String REVIEW_SCHEMA =
      """
      {"type":"object","required":["script","nameWarnings"],"properties":{
       "script":SCRIPT,"nameWarnings":{"type":"array","items":{"type":"string"}}}}
      """
          .replace("SCRIPT", SCRIPT_SCHEMA.strip());
}
