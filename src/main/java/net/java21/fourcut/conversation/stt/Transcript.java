package net.java21.fourcut.conversation.stt;

import java.util.List;

/**
 * 전사 결과 (docs specs/001 pipeline-schemas.md). 화자는 A, B, C… 가명만 쓴다.
 *
 * @param language 감지한 언어 (ko, en)
 */
public record Transcript(String language, List<String> speakers, List<Utterance> utterances) {

  /** 발화 하나. 시각은 대화 시작부터의 초. */
  public record Utterance(String speaker, double start, double end, String text) {}

  /** 마지막 발화가 끝난 시각(초). */
  public int durationSec() {
    return (int) Math.ceil(utterances.stream().mapToDouble(Utterance::end).max().orElse(0));
  }

  /** [from, to] 구간과 겹치는 발화. */
  public List<Utterance> between(double from, double to) {
    return utterances.stream().filter(u -> u.end() >= from && u.start() <= to).toList();
  }

  /** LLM에 넣는 한 줄 형식: {@code [12:30] A: 내용}. */
  public static String lines(List<Utterance> utterances) {
    StringBuilder sb = new StringBuilder();
    for (Utterance u : utterances) {
      int t = (int) u.start();
      sb.append(String.format(java.util.Locale.ROOT, "[%02d:%02d] ", t / 60, t % 60))
          .append(u.speaker())
          .append(": ")
          .append(u.text())
          .append('\n');
    }
    return sb.toString();
  }
}
