package net.java21.fourcut.conversation.scenario;

/**
 * 장면 후보 (docs specs/001 pipeline-schemas.md).
 *
 * @param kind FUNNY(즐거운 대화), TOUCHING(남기고 싶은 대화), IMPORTANT(결정·약속·소식), MEMORY(추억), CORE(대화의 핵심 주제)
 * @param score 0~1. 항목 점수가 있으면 코드가 계산한다({@link #scored()})
 * @param selected 만화로 만들 장면이면 true. 대화당 정확히 1개
 * @param story 이야기성 1~5: 상황 → 사건 → 반응 → 결말이 이 구간 안에 다 나오는가
 * @param visual 그림으로 보이는가 1~5: 행동·장소·물건이 있는가
 * @param relatable 공감 1~5: 대화한 사람들이 보고 "맞아, 이랬지" 할 장면인가
 * @param intro 인사·안부·지각 사과 같은 도입부인가
 * @param concept 이 장면에 어울리는 그림 컨셉({@link Concept} 이름). 사용자가 컨셉을 고르지 않았을 때 쓴다
 * @param peak 장면을 특별하게 만드는 한 마디(대화 그대로). 콘티의 전 컷에 쓴다
 * @param memorable 간직 1~5: 몇 년 뒤에도 꺼내 보고 싶은 순간인가
 * @param flow 이 장면을 만화로 살릴 흐름 한 줄(무엇을 어떻게 살리는지). 정해진 목록 없이 장면마다 LLM이 정한다. 콘티의 "만화 분위기"에 더한다
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
    Boolean intro,
    String concept,
    String peak,
    Integer memorable,
    String flow) {

  static final java.util.Set<String> KINDS =
      java.util.Set.of("FUNNY", "TOUCHING", "IMPORTANT", "MEMORY", "CORE");

  /** 도입부 감점. */
  static final double INTRO_PENALTY = 0.3;

  public SceneCandidate(
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
      Boolean intro,
      String concept,
      String peak,
      Integer memorable) {
    this(
        kind, start, end, summary, reason, score, selected, story, visual, relatable, intro,
        concept, peak, memorable, null);
  }

  public SceneCandidate(
      String kind,
      double start,
      double end,
      String summary,
      String reason,
      double score,
      Boolean selected) {
    this(kind, start, end, summary, reason, score, selected, null, null, null, null, null);
  }

  public SceneCandidate(
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
      Boolean intro,
      String concept) {
    this(
        kind, start, end, summary, reason, score, selected, story, visual, relatable, intro,
        concept, null, null);
  }

  boolean valid() {
    return KINDS.contains(kind)
        && end > start
        && summary != null
        && !summary.isBlank()
        && score >= 0
        && score <= 1;
  }

  /**
   * 항목 점수로 score를 다시 계산한다. 간직 점수가 있으면 (이야기성 0.3 + 그림 0.2 + 공감 0.2 + 간직 0.3) / 5, 없으면 (이야기성 0.4 + 그림
   * 0.3 + 공감 0.3) / 5. 도입부면 0.3을 뺀다. LLM이 내는 score 하나는 0.8~0.9에 몰려 장면을 가리지 못했다(GPU PC 반복 실행에서 샘플
   * 01은 9번 모두 대화 첫 인사 장면을 골랐다). 항목 점수가 없으면 그대로 둔다.
   */
  SceneCandidate scored() {
    if (story == null || visual == null || relatable == null) {
      return this;
    }
    double s =
        memorable == null
            ? (0.4 * clamp(story) + 0.3 * clamp(visual) + 0.3 * clamp(relatable)) / 5
            : (0.3 * clamp(story)
                    + 0.2 * clamp(visual)
                    + 0.2 * clamp(relatable)
                    + 0.3 * clamp(memorable))
                / 5;
    if (Boolean.TRUE.equals(intro)) {
      s -= INTRO_PENALTY;
    }
    double rounded = Math.round(Math.max(0, s) * 100) / 100.0;
    return new SceneCandidate(
        kind, start, end, summary, reason, rounded, selected, story, visual, relatable, intro,
        concept, peak, memorable, flow);
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
        kind, start, end, summary, reason, score, true, story, visual, relatable, intro, concept,
        peak, memorable, flow);
  }
}
