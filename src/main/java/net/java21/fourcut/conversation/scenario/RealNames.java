package net.java21.fourcut.conversation.scenario;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.stt.Transcript;

/**
 * 대화 속 실명을 콘티 LLM에 넣기 전에 가명으로 바꾼다. 실명은 장면 추출 LLM이 찾아 둔다(names). 등장 화자의 이름이면 그 화자의 가명으로, 누구인지 모르면
 * "친구"로 바꾼다. 한국어 이름 뒤 호격·조사는 받침에 맞춘다("민지야" → "보람아", "민지가" → "보람이가").
 */
final class RealNames {

  static final String UNKNOWN = "친구";

  /** 이름이 아닌 호칭·직함. LLM이 이름으로 잘못 내도 버린다. */
  private static final Set<String> TITLES =
      Set.of(
          "팀장", "부장", "과장", "차장", "대리", "사원", "사장", "실장", "선배", "후배", "막내", "총무", "언니", "오빠", "누나",
          "형님", "엄마", "아빠", "친구", "선생", "교수", "이모", "삼촌", "고모", "할머니", "할아버지");

  private static final Pattern HANGUL_NAME = Pattern.compile("[가-힣]{2,4}");

  private RealNames() {}

  /** 실명으로 볼 만한 단어인지: 한글 2~4자, 대화에 나오고, 호칭·직함(…님 포함)이 아니다. */
  static boolean plausible(String word, String spokenText) {
    return word != null
        && HANGUL_NAME.matcher(word).matches()
        && spokenText.contains(word)
        && !word.endsWith("님")
        && !TITLES.contains(word)
        && !CastNames.POOL.contains(word);
  }

  /** 실명 → 그 이름으로 불리는 화자. 화자가 둘이고 한 화자가 "민지야"처럼 부르면 그 이름은 다른 화자다. 알 수 없으면 빈 문자열("친구"로 바뀐다). */
  static Map<String, String> whose(List<String> names, Transcript transcript) {
    Map<String, String> out = new LinkedHashMap<>();
    List<String> speakers = transcript.speakers();
    for (String name : names) {
      String who = "";
      if (speakers.size() == 2) {
        Pattern vocative = Pattern.compile(Pattern.quote(name) + "(?:야|아)(?![가-힣])");
        Set<String> callers = new HashSet<>();
        for (Transcript.Utterance u : transcript.utterances()) {
          if (vocative.matcher(u.text()).find()) {
            callers.add(u.speaker());
          }
        }
        if (callers.size() == 1) {
          String caller = callers.iterator().next();
          who = speakers.get(0).equals(caller) ? speakers.get(1) : speakers.get(0);
        }
      }
      out.put(name, who);
    }
    return out;
  }

  /**
   * 바꿀 단어 → 바꿀 말.
   *
   * @param found 실명 → 그 사람의 화자 표시(모르면 빈 문자열)
   * @param cast 화자 표시 → 가명
   */
  static Map<String, String> replacements(Map<String, String> found, Map<String, String> cast) {
    Map<String, String> out = new LinkedHashMap<>();
    found.forEach(
        (name, speaker) -> {
          String n = name == null ? "" : name.strip();
          if (n.length() < 2 || cast.containsKey(n) || cast.containsValue(n)) {
            return;
          }
          String alias = cast.getOrDefault(speaker == null ? "" : speaker.strip(), UNKNOWN);
          add(out, n, alias);
        });
    return out;
  }

  private static void add(Map<String, String> out, String name, String alias) {
    boolean aliasFinal = hasFinal(alias);
    String vocative = alias + (aliasFinal ? "아" : "야");
    out.put(name + "야", vocative);
    out.put(name + "아", vocative);
    if (hasFinal(name)) {
      // "민준이가", "민준이랑": 받침 있는 이름 뒤 "이"
      out.put(name + "이", aliasFinal ? alias + "이" : alias);
      out.put(name, alias);
    } else {
      // "민지가" → "보람이가": 받침 있는 가명은 구어체로 "이"를 붙인다
      out.put(name, aliasFinal && !UNKNOWN.equals(alias) ? alias + "이" : alias);
    }
  }

  /** 마지막 글자가 받침 있는 한글인지. */
  static boolean hasFinal(String word) {
    if (word == null || word.isEmpty()) {
      return false;
    }
    char c = word.charAt(word.length() - 1);
    return c >= '가' && c <= '힣' && (c - '가') % 28 != 0;
  }

  /** 긴 단어부터 바꾼다("민준이"가 "민준"보다 먼저). */
  static String apply(String text, Map<String, String> replacements) {
    if (text == null || replacements.isEmpty()) {
      return text;
    }
    List<String> keys =
        replacements.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    String out = text;
    for (String k : keys) {
      out = out.replace(k, replacements.get(k));
    }
    return out;
  }
}
