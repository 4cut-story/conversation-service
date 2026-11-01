package net.java21.fourcut.conversation.pipeline;

/**
 * 파이프라인 진행을 대화 상태에 반영하는 쪽 (conversations 모듈이 구현한다). 파이프라인은 대화 모듈을 직접 모른다. 모든 메서드는 파이프라인 트랜잭션 안에서
 * 불린다.
 */
public interface PipelineObserver {

  /** 대화 정보. 없으면(지워진 대화) null. */
  ConversationInfo find(Long conversationId);

  /** 단계가 시작됐다. */
  void stepStarted(Long conversationId, Step step);

  /** 전사가 끝나 언어와 길이를 알게 됐다. */
  void transcribed(Long conversationId, String language, int durationSec);

  /**
   * 만화가 완성돼 만화함에 들어갔다.
   *
   * @param elapsedSec 처리 시작(전사 시작)부터 완성까지 걸린 시간
   */
  void completed(Long conversationId, String comicId, int elapsedSec);

  /** 만화로 만들 장면을 찾지 못했다. */
  void noScene(Long conversationId);

  /** 재시도를 다 썼다. reason은 사용자에게 보여 줄 문장이다. */
  void failed(Long conversationId, String reason);
}
