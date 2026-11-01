package net.java21.fourcut.conversation.scenario;

/**
 * LLM 프롬프트와 출력 JSON 스키마. 바꾸면 VERSION을 올린다 (단계 기록 model_info에 남는다).
 *
 * <p>qwen3 thinking은 워커가 끈다(think=false). 그래서 "속으로 여러 안을 만들어 고르라" 같은 지시는 두지 않는다. 맞춤법·실명 검수도 콘티 호출 한
 * 번에 함께 시킨다(LLM 호출 1회 절약).
 */
final class Prompts {

  static final String VERSION = "2026-11-01.1";

  private Prompts() {}

  /** 장면 후보 JSON은 짧다. 넘치면 잘린 JSON이 되어 재시도한다. */
  static final int EXTRACT_MAX_TOKENS = 600;

  /** 콘티 JSON(컷 4개, 대사, 인물) 출력 상한. */
  static final int SCRIPT_MAX_TOKENS = 1200;

  static final String EXTRACT_SYSTEM =
      """
      친구들 대화 전사에서 4컷 만화로 만들 장면을 고른다.
      - kind: FUNNY(웃음, 반전, 엉뚱한 말) 또는 IMPORTANT(약속, 결정, 소식).
      - 최대 2개. 없으면 빈 목록. 욕설·민감한 주제(건강, 정치, 성, 혐오, 개인정보)가 중심이면 고르지 않는다.
      - start, end는 대화 시작부터의 초. 장면 길이는 30초~3분.
      - summary 한 줄, reason 한 줄. 사람은 화자 표시(A, B…)로 쓴다.
      - score 0~1: 만화로 만들었을 때 재미·의미.
      - 대화와 같은 언어로 쓴다.
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
      대화 장면을 기-승-전-결 4컷 만화 콘티로 만든다.

      인물 (characters, 화자 A, B…마다 하나)
      - name: 한국어 가명 2~3글자(예: 민지, 도윤). 대화에 나온 이름·별명과 겹치면 안 된다.
      - persona: 이 장면에서 드러난 성격 한 줄(예: 먹는 데 진심인 친구).
      - look: 그림용 영어 외모 6단어 이내, 성별·나이 명사로 시작(예: "girl, short black hair, yellow hoodie").
        name과 성별이 어울리게 정한다. 실제 외모는 추측하지 않는다.

      컷 (panels, 정확히 4개, role 순서 "기","승","전","결")
      - 네 컷은 서로 다른 순간이다. 같은 행동·같은 구도를 반복하지 않는다.
        기: 장소와 인물 소개 / 승: 사건이 생김 / 전: 반전·감정이 가장 큰 순간 / 결: 결과·여운.
      - shot: wide, medium, close-up 중 하나. 기는 보통 wide, 전은 보통 close-up. 네 컷이 모두 같으면 안 된다.
      - scene: 그림 모델용 영어 12단어 이내. 인물은 {A}, {B}처럼 쓰고 동사 하나로 행동을 쓴 뒤 장소를 붙인다.
        예: "{A} drops ice cream, {B} gasps, park"
      - lines: 컷당 0~2개, 한 문장 30자 이내. 실제 말투를 살려 짧게, 맞춤법을 맞춘다. speaker는 A, B…를 쓴다.
      - 대사에 실명·별명·학교·회사 이름이 나오면 가명이나 "친구"로 바꾸고 원래 단어를 nameWarnings에 넣는다.
      - 욕설과 민감한 표현은 순화한다.

      title은 20자 이내. 대사·제목·name·persona는 대화와 같은 언어로 쓴다.
      """;

  static final String SCRIPT_SCHEMA =
      """
      {"type":"object","required":["title","characters","panels","nameWarnings"],"properties":{
       "title":{"type":"string"},
       "characters":{"type":"array","minItems":1,"items":{"type":"object",
        "required":["speaker","name","persona","look"],"properties":{"speaker":{"type":"string"},
         "name":{"type":"string"},"persona":{"type":"string"},"look":{"type":"string"}}}},
       "panels":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"object",
        "required":["role","shot","scene","lines"],"properties":{"role":{"type":"string","enum":["기","승","전","결"]},
         "shot":{"type":"string","enum":["wide","medium","close-up"]},
         "scene":{"type":"string"},"lines":{"type":"array","maxItems":2,"items":{"type":"object",
          "required":["speaker","text"],"properties":{"speaker":{"type":"string"},"text":{"type":"string"}}}}}}},
       "nameWarnings":{"type":"array","items":{"type":"string"}}}}
      """;
}
