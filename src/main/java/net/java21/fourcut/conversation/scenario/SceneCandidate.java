package net.java21.fourcut.conversation.scenario;

/**
 * 장면 후보 (docs specs/001 pipeline-schemas.md).
 *
 * @param kind CORE(대화의 핵심 주제), FUNNY(웃음 포인트), IMPORTANT(결정·약속·소식·감정)
 * @param score 0~1. 항목 점수가 있으면 코드가 계산한다({@link #scored()})
 * @param selected 만화로 만들 장면이면 true. 대화당 정확히 1개
 * @param story 이야기성 1~5: 상황 → 사건 → 반응 → 결말이 이 구간 안에 다 나오는가
 * @param visual 그림으로 보이는가 1~5: 행동·장소·물건이 있는가
 * @param relatable 공감 1~5: 대화한 사람들이 보고 "맞아, 이랬지" 할 장면인가
 * @param intro 인사·안부·지각 사과 같은 도입부인가
 */
public record SceneCandidate(
    String kind,
    double start,
    double end,
    String summary,
    String reason,
    double score,
    Boolean selected,
    Integer story,
    Integer visual,
    Integer relatable,
    Boolean intro) {

  /** 도입부 감점. */
  static final double INTRO_PENALTY = 0.3;

  public SceneCandidate(
      String kind,
      double start,
      double end,
      String summary,
      String reason,
      double score,
      Boolean selected) {
    this(kind, start, end, summary, reason, score, selected, null, null, null, null);
  }

  boolean valid() {
    return ("CORE".equals(kind) || "FUNNY".equals(kind) || "IMPORTANT".equals(kind))
        && end > start
        && summary != null
        && !summary.isBlank()
        && score >= 0
        && score <= 1;
  }

  /**
   * 항목 점수로 score를 다시 계산한다: (이야기성 0.4 + 그림 0.3 + 공감 0.3) / 5, 도입부면 0.3을 뺀다. LLM이 내는 score 하나는
   * 0.8~0.9에 몰려 장면을 가리지 못했다(GPU PC 반복 실행에서 샘플 01은 9번 모두 대화 첫 인사 장면을 골랐다). 항목 점수가 없으면 그대로 둔다.
   */
  SceneCandidate scored() {
    if (story == null || visual == null || relatable == null) {
      return this;
    }
    double s = (0.4 * clamp(story) + 0.3 * clamp(visual) + 0.3 * clamp(relatable)) / 5;
    if (Boolean.TRUE.equals(intro)) {
      s -= INTRO_PENALTY;
    }
    double rounded = Math.round(Math.max(0, s) * 100) / 100.0;
    return new SceneCandidate(
        kind, start, end, summary, reason, rounded, selected, story, visual, relatable, intro);
  }

  private static int clamp(int v) {
    return Math.max(1, Math.min(5, v));
  }

  /** 고른 장면인지 (값이 없으면 아님). */
  boolean chosen() {
    return Boolean.TRUE.equals(selected);
  }

  SceneCandidate select() {
    return new SceneCandidate(
        kind, start, end, summary, reason, score, true, story, visual, relatable, intro);
  }
}
