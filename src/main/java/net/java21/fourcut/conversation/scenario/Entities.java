package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * 실존 기업·브랜드·단체 이름과 실존 인물(정치인, 연예인, 기업 총수 등)을 가리키는 말을 일반 표현으로 바꾼다("삼성전자가" → "대기업이", "OO그룹 회장" → "한
 * 회사 회장"). 만화가 누군가를 지칭하지 않게 한다.
 *
 * <p>두 겹으로 거른다. 장면 추출 LLM이 대화에서 찾아 일반 표현을 붙인 말(entities)과, LLM이 놓쳐도 걸리는 이 클래스의 사전·이름 꼴(…그룹,
 * …홀딩스)이다. 콘티 LLM에 넣기 전 대화와, LLM이 쓴 콘티(제목, 대사, 줄거리, 인물 소개)에 모두 쓴다. 뒤의 조사는 바꾼 말의 받침에 맞춘다.
 */
final class Entities {

  /** 일반 표현 길이 상한. LLM이 설명문을 내면 버린다. */
  static final int MAX_GENERIC = 12;

  /** 사전: 실존 기업 → 일반 표현. 다른 뜻으로도 흔히 쓰는 말(현대, 기아, 아마존, 메타)은 넣지 않는다. 긴 이름부터 찾는다. */
  static final Map<String, String> KNOWN = known();

  private static Map<String, String> known() {
    Map<String, String> m = new LinkedHashMap<>();
    for (String w :
        List.of(
            "삼성전자", "삼성그룹", "삼성", "현대자동차", "현대차", "LG전자", "LG", "SK하이닉스", "SK", "롯데", "한화", "신세계",
            "CJ", "포스코", "GS", "두산")) {
      m.put(w, "대기업");
    }
    for (String w : List.of("카카오", "네이버", "쿠팡", "배달의민족")) {
      m.put(w, "IT 기업");
    }
    for (String w : List.of("애플", "구글", "테슬라", "엔비디아", "마이크로소프트", "오픈AI")) {
      m.put(w, "외국 기업");
    }
    m.put("대한항공", "항공사");
    m.put("아시아나", "항공사");
    m.put("셀트리온", "제약 회사");
    m.put("하이브", "기획사");
    return m;
  }

  /** 단어 앞뒤 경계: 한글·영문·숫자가 붙어 있으면 다른 낱말의 일부다("삼성동", "애플파이"). */
  private static final String BEFORE = "(?<![가-힣A-Za-z0-9])";

  /** 이름 뒤에 붙을 수 있는 조사. 조사 뒤에는 낱말이 끝나야 한다. */
  private static final String PARTICLES =
      "(?:이랑|이나|이라|에서|에게|한테|으로|로|처럼|보다|까지|부터|만|도|은|는|이|가|을|를|의|에|와|과|랑)?";

  private static final String AFTER = "(?![가-힣A-Za-z0-9])";

  private static final Pattern KNOWN_WORD =
      Pattern.compile(
          BEFORE
              + "("
              + String.join(
                  "|",
                  KNOWN.keySet().stream()
                      .sorted((x, y) -> y.length() - x.length())
                      .map(Pattern::quote)
                      .toList())
              + ")("
              + PARTICLES
              + ")"
              + AFTER);

  /** 회사 이름 꼴: 붙여 쓴 "OO그룹", "OO홀딩스", "OO중공업", "OO물산", "OO엔터테인먼트". */
  private static final Pattern COMPANY_FORM =
      Pattern.compile(
          BEFORE
              + "((?!아이돌|보이|스터디|밴드|댄스|채팅|단톡|모임|독서|운동|연구|실험|친구|동호)[가-힣A-Za-z0-9]{2,10}"
              + "(?:그룹|홀딩스|중공업|물산|엔터테인먼트))("
              + PARTICLES
              + ")"
              + AFTER);

  private Entities() {}

  /**
   * 장면 추출 LLM이 낸 entities({@code [{name, generic}]})에서 대화에 실제로 나오는 것만 고른다.
   *
   * @param known 지금까지 모은 것
   */
  static Map<String, String> merge(Map<String, String> known, JsonNode found, String spokenText) {
    Map<String, String> out = new LinkedHashMap<>(known);
    for (JsonNode n : found) {
      String name = n.path("name").asString("").strip();
      String generic = n.path("generic").asString("").strip();
      if (name.length() >= 2
          && spokenText.contains(name)
          && !generic.isEmpty()
          && generic.length() <= MAX_GENERIC
          && !generic.contains(name)
          && !CastNames.POOL.contains(name)) {
        out.putIfAbsent(name, generic);
      }
    }
    return out;
  }

  /** LLM이 찾은 말과 사전·이름 꼴을 모두 일반 표현으로 바꾼다. */
  static String apply(String text, Map<String, String> found) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String out = found.isEmpty() ? text : CastNames.rename(text, found);
    out = replace(out, KNOWN_WORD, KNOWN::get);
    return replace(out, COMPANY_FORM, w -> "한 회사");
  }

  /** 글에 남은 실존 기업 이름(사전·이름 꼴). 생성 후 검사 기록에 쓴다. */
  static List<String> remaining(String text) {
    List<String> out = new ArrayList<>();
    if (text == null) {
      return out;
    }
    for (Pattern p : List.of(KNOWN_WORD, COMPANY_FORM)) {
      Matcher m = p.matcher(text);
      while (m.find()) {
        out.add(m.group(1));
      }
    }
    return out;
  }

  private static String replace(
      String text, Pattern pattern, java.util.function.UnaryOperator<String> generic) {
    Matcher m = pattern.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String to = generic.apply(m.group(1));
      String particle = m.group(2);
      String fixed =
          particle.isEmpty() ? to : CastNames.rename(m.group(1) + particle, Map.of(m.group(1), to));
      m.appendReplacement(sb, Matcher.quoteReplacement(fixed));
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
