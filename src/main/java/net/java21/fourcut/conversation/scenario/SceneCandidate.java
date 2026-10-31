package net.java21.fourcut.conversation.scenario;

/**
 * 장면 후보 (docs specs/001 pipeline-schemas.md).
 *
 * @param kind FUNNY(웃음 포인트) 또는 IMPORTANT(중요한 일)
 * @param selected 만화로 만들 장면이면 true. 대화당 정확히 1개
 */
public record SceneCandidate(
    String kind,
    double start,
    double end,
    String summary,
    String reason,
    double score,
    Boolean selected) {

  boolean valid() {
    return ("FUNNY".equals(kind) || "IMPORTANT".equals(kind))
        && end > start
        && summary != null
        && !summary.isBlank()
        && score >= 0
        && score <= 1;
  }

  /** 고른 장면인지 (값이 없으면 아님). */
  boolean chosen() {
    return Boolean.TRUE.equals(selected);
  }

  SceneCandidate select() {
    return new SceneCandidate(kind, start, end, summary, reason, score, true);
  }
}
