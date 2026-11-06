package net.java21.fourcut.conversation.scenario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 화자(A, B…)마다 가명을 코드로 정한다. LLM에 맡기면 대화 속 실명을 그대로 가명으로 고르는 일이 잦아서(qwen3:8b), 대화 원문에 나오지 않는 이름만 고른다.
 * 같은 대화는 같은 가명을 받는다.
 */
final class CastNames {

  /** 성별 구분 없는 가명. 대화에 나온 이름은 건너뛴다. */
  static final List<String> POOL =
      List.of(
          "하늘", "지우", "유진", "해솔", "다온", "시온", "한결", "가온", "나래", "보람", "새봄", "이랑", "윤슬", "단비", "도담",
          "라온", "아름", "여름", "온유", "은솔", "주원", "채온", "하랑", "해담");

  private CastNames() {}

  static Map<String, String> assign(List<String> speakers, String spokenText, long seed) {
    List<String> free = POOL.stream().filter(n -> !spokenText.contains(n)).toList();
    Map<String, String> names = new LinkedHashMap<>();
    if (free.isEmpty()) {
      return names;
    }
    int start = (int) Math.floorMod(seed, (long) free.size());
    for (int i = 0; i < speakers.size() && i < free.size(); i++) {
      names.put(speakers.get(i), free.get((start + i) % free.size()));
    }
    return names;
  }

  /** 받침에 따라 모양이 바뀌는 조사: 받침 있을 때, 없을 때. 긴 것부터 찾는다. */
  private static final List<String[]> PARTICLES =
      List.of(
          new String[] {"이에요", "예요"},
          new String[] {"이랑", "랑"},
          new String[] {"이나", "나"},
          new String[] {"으로", "로"},
          new String[] {"은", "는"},
          new String[] {"이", "가"},
          new String[] {"을", "를"},
          new String[] {"과", "와"},
          new String[] {"아", "야"});

  /** 받침 있는 이름 뒤 구어체 "이"("하늘이가", "하늘이도") 다음에 오는 말의 첫 글자. */
  private static final String AFTER_FILLER = "가는도랑를한만와야나의께";

  /**
   * 글 속 이름을 새 이름으로 한 번에 바꾸고(긴 이름부터), 뒤의 조사와 구어체 "이"를 새 이름의 받침에 맞춘다("하늘아" → "민지야", "하늘이가" → "민지가",
   * "하늘은" → "민지는"). 바꾼 이름이 다른 옛 이름과 같아도 다시 바뀌지 않는다.
   *
   * @param names 옛 이름 → 새 이름
   */
  static String rename(String text, Map<String, String> names) {
    if (text == null || names.isEmpty()) {
      return text;
    }
    List<String> keys =
        names.keySet().stream()
            .filter(k -> k != null && !k.isEmpty())
            .sorted((x, y) -> y.length() - x.length())
            .toList();
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      String from = null;
      for (String k : keys) {
        if (text.startsWith(k, i)) {
          from = k;
          break;
        }
      }
      if (from == null) {
        out.append(text.charAt(i));
        i++;
        continue;
      }
      String to = names.get(from);
      out.append(to);
      i = afterName(text, i + from.length(), RealNames.hasFinal(from), to, out);
    }
    return out.toString();
  }

  /** 이름 바로 뒤(j)의 구어체 "이"나 조사를 새 이름 to에 맞춰 붙이고, 다음에 읽을 위치를 돌려준다. */
  private static int afterName(
      String text, int j, boolean fromFinal, String to, StringBuilder out) {
    boolean toFinal = RealNames.hasFinal(to);
    if (fromFinal
        && j + 1 < text.length()
        && text.charAt(j) == '이'
        && AFTER_FILLER.indexOf(text.charAt(j + 1)) >= 0) {
      // "하늘이가": 받침 있는 이름 뒤 "이" 다음 조사는 이미 받침 없는 꼴이다
      out.append(toFinal ? "이" : "");
      return j + 1;
    }
    for (String[] p : PARTICLES) {
      String found = text.startsWith(p[0], j) ? p[0] : text.startsWith(p[1], j) ? p[1] : null;
      if (found != null) {
        out.append(particle(p, to, toFinal));
        return j + found.length();
      }
    }
    return j;
  }

  private static String particle(String[] p, String to, boolean toFinal) {
    if ("으로".equals(p[0]) && toFinal && endsWithRieul(to)) {
      return "로";
    }
    return toFinal ? p[0] : p[1];
  }

  private static boolean endsWithRieul(String word) {
    char c = word.charAt(word.length() - 1);
    return (c - '가') % 28 == 8;
  }

  /** 프롬프트 한 줄: {@code A=하늘, B=도윤}. */
  static String line(Map<String, String> names) {
    StringBuilder sb = new StringBuilder();
    names.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(k).append('=').append(v));
    return sb.toString();
  }
}
