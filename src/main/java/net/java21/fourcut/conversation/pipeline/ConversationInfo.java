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
 */
public record ConversationInfo(
    Long id,
    String publicId,
    String ownerId,
    String language,
    int chunkCount,
    String concept,
    String dialect,
    boolean manualLlm) {

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
