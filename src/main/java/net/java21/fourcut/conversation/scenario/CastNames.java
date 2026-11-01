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

  /** 프롬프트 한 줄: {@code A=하늘, B=도윤}. */
  static String line(Map<String, String> names) {
    StringBuilder sb = new StringBuilder();
    names.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(k).append('=').append(v));
    return sb.toString();
  }
}
