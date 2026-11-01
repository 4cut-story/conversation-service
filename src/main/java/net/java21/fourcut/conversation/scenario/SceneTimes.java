package net.java21.fourcut.conversation.scenario;

import java.util.List;
import net.java21.fourcut.conversation.stt.Transcript;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 장면 후보의 시간 구간을 코드로 정한다. LLM은 초 계산을 자주 틀려서("04:11"을 40초로 쓰는 등) 장면 첫 줄과 마지막 줄의 [분:초] 표시만 받고, 실제 시각은
 * 그 줄의 발화에서 가져온다. 30초보다 짧으면 뒤 발화를 더하고 3분보다 길면 자른다.
 */
final class SceneTimes {

  static final double MIN_SEC = 30;
  static final double MAX_SEC = 180;

  private SceneTimes() {}

  /** "04:11" → 251. 형식이 다르면 -1. */
  static int seconds(String tag) {
    if (tag == null) {
      return -1;
    }
    String[] p = tag.strip().split(":");
    if (p.length != 2) {
      return -1;
    }
    try {
      int m = Integer.parseInt(p[0]);
      int s = Integer.parseInt(p[1]);
      return m < 0 || s < 0 || s >= 60 ? -1 : m * 60 + s;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * from/to 표시를 start/end(초)로 바꾼 후보. 표시가 없거나 맞는 줄이 없으면 start=end=0(검증에서 버려진다).
   *
   * @param lines LLM에 보여 준 줄(TranscriptText.merge 결과)
   */
  static JsonNode resolve(JsonNode node, List<Transcript.Utterance> lines) {
    if (!(node instanceof ObjectNode obj) || !node.has("from")) {
      return node;
    }
    int first = index(lines, seconds(node.path("from").asString("")));
    int last = index(lines, seconds(node.path("to").asString("")));
    obj.remove("from");
    obj.remove("to");
    if (first < 0 || last < first) {
      obj.put("start", 0);
      obj.put("end", 0);
      return obj;
    }
    double start = lines.get(first).start();
    double end = lines.get(last).end();
    while (end - start < MIN_SEC && last + 1 < lines.size()) {
      last++;
      end = lines.get(last).end();
    }
    obj.put("start", start);
    obj.put("end", Math.min(end, start + MAX_SEC));
    return obj;
  }

  /** 표시와 같은 시각(초 단위 버림)에 시작하는 줄. 없으면 그 시각을 품은 줄, 그것도 없으면 -1. */
  private static int index(List<Transcript.Utterance> lines, int sec) {
    if (sec < 0) {
      return -1;
    }
    for (int i = 0; i < lines.size(); i++) {
      if ((int) lines.get(i).start() == sec) {
        return i;
      }
    }
    for (int i = 0; i < lines.size(); i++) {
      Transcript.Utterance u = lines.get(i);
      if (u.start() <= sec && sec <= u.end()) {
        return i;
      }
    }
    return -1;
  }
}
