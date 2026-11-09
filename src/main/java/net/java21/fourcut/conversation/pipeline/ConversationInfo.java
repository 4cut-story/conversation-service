package net.java21.fourcut.conversation.pipeline;

/**
 * 단계가 쓰는 대화 정보.
 *
 * @param id 내부 ID (pipeline_steps·gpu_jobs의 외래 키)
 * @param publicId 공개 ID. MinIO 객체 키와 다른 서비스에 넘기는 대화 ID
 * @param ownerId 소유자 공개 ID
 * @param language 사용자가 고른 언어. 없으면 null(자동 감지)
 * @param concept 사용자가 고른 만화 컨셉. 없으면 null(자동)
 * @param dialect 사용자가 고른 대사 사투리. 없으면 null(사투리 없음)
 * @param manualLlm 테스트 전용. LLM 작업을 서버가 처리하지 않고 주인이 직접 답을 넣는다
 * @param article 기사로 만든 대화의 원문 출처. 녹음이면 null
 */
public record ConversationInfo(
    Long id,
    String publicId,
    String ownerId,
    String language,
    int chunkCount,
    String concept,
    String dialect,
    boolean manualLlm,
    ArticleSource article) {

  /**
   * 기사 원문 출처. 만화 제작 정보에 남아 게시물에 출처 링크로 보인다.
   *
   * @param url 기사 주소
   * @param title 기사 제목
   * @param site 사이트 이름
   */
  public record ArticleSource(String url, String title, String site) {}

  public ConversationInfo(
      Long id,
      String publicId,
      String ownerId,
      String language,
      int chunkCount,
      String concept,
      String dialect,
      boolean manualLlm) {
    this(id, publicId, ownerId, language, chunkCount, concept, dialect, manualLlm, null);
  }

  public ConversationInfo(
      Long id,
      String publicId,
      String ownerId,
      String language,
      int chunkCount,
      String concept,
      String dialect) {
    this(id, publicId, ownerId, language, chunkCount, concept, dialect, false);
  }

  public ConversationInfo(
      Long id, String publicId, String ownerId, String language, int chunkCount, String concept) {
    this(id, publicId, ownerId, language, chunkCount, concept, null);
  }

  public ConversationInfo(
      Long id, String publicId, String ownerId, String language, int chunkCount) {
    this(id, publicId, ownerId, language, chunkCount, null);
  }
}
