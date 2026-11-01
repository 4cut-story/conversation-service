package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 콘티 대사를 대화 원문에 맞춘다. qwen3는 "섰어"를 "석어"로 바꾸는 식으로 옮겨 적으며 말을 틀리게 하고, 여러 문장을 한 말풍선에 몰아넣기도 한다. 대사 문장마다
 * 원문에서 가장 비슷한 문장을 찾아 충분히 비슷하면 원문으로 바꾸고, 말풍선 길이를 넘는 뒷문장은 버린다.
 */
final class SpokenLines {

  /** 말풍선 하나의 글자 수 상한. 첫 문장은 길어도 남긴다. */
  static final int MAX_CHARS = 40;

  /** 이만큼 비슷하면 같은 문장으로 본다(편집 거리 기준). */
  static final double MIN_SIMILARITY = 0.75;

  private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!])\\s+");

  private SpokenLines() {}

  static List<String> sentences(String text) {
    List<String> out = new ArrayList<>();
    for (String s : SENTENCE_END.split(text == null ? "" : text.strip())) {
      if (!s.isBlank()) {
        out.add(s.strip());
      }
    }
    return out;
  }

  /** 대사를 원문 문장으로 맞추고 길이를 줄인다. */
  static String snap(String line, List<String> source) {
    if (line == null) {
      return null;
    }
    StringBuilder out = new StringBuilder();
    for (String s : sentences(line)) {
      String fixed = closest(s, source);
      if (!out.isEmpty() && out.length() + 1 + fixed.length() > MAX_CHARS) {
        break;
      }
      out.append(out.isEmpty() ? "" : " ").append(fixed);
    }
    return out.isEmpty() ? line.strip() : out.toString();
  }

  /** 대사의 첫 문장이 장면 대화(sceneSentences)에 있는 말인지(편집 거리로 충분히 비슷하거나 그 안에 들어 있다). */
  static boolean inside(String line, List<String> sceneSentences) {
    List<String> parts = sentences(line);
    if (parts.isEmpty()) {
      return false;
    }
    String first = parts.get(0);
    for (String s : sceneSentences) {
      if (s.contains(first) || similarity(first, s) >= MIN_SIMILARITY) {
        return true;
      }
    }
    return false;
  }

  private static String closest(String sentence, List<String> source) {
    String best = sentence;
    double score = MIN_SIMILARITY;
    for (String candidate : source) {
      double sim = similarity(sentence, candidate);
      if (sim >= score) {
        score = sim;
        best = candidate;
      }
    }
    return best;
  }

  /** 1 - 편집 거리 / 긴 쪽 길이. */
  static double similarity(String a, String b) {
    int max = Math.max(a.length(), b.length());
    return max == 0 ? 1 : 1 - (double) distance(a, b) / max;
  }

  private static int distance(String a, String b) {
    int[] prev = new int[b.length() + 1];
    int[] cur = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      prev[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      cur[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
      }
      int[] t = prev;
      prev = cur;
      cur = t;
    }
    return prev[b.length()];
  }
}
