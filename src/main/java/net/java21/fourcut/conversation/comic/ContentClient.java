package net.java21.fourcut.conversation.comic;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import tools.jackson.databind.JsonNode;

/** content-service 내부 API. 같은 대화로 다시 보내면 기존 만화 ID를 돌려준다(멱등). */
public interface ContentClient {

  /**
   * 만화 등록 요청.
   *
   * @param cast 만화 아래에 보이는 등장인물 소개
   * @param concept 그린 그림체({@code Concept} 이름). 자동이면 서버가 고른 그림체
   * @param dialect 대사 사투리({@code Dialect} 이름). 사투리 없음은 NONE
   * @param summary 만화 아래에 보이는 줄거리 요약. 예전 콘티면 null
   * @param production 제작 정보 ({@link net.java21.fourcut.conversation.pipeline.Production}). 못 만들면
   *     null
   * @param live 라이브 모드 재생 정보 (common contracts/live-mode.yaml LivePlayback). 못 만들면 null
   */
  record RegisterComic(
      String ownerId,
      String conversationId,
      String title,
      String imageKey,
      String thumbKey,
      String language,
      List<String> nameWarnings,
      List<CastMember> cast,
      String concept,
      String dialect,
      String summary,
      JsonNode production,
      JsonNode live) {}

  /**
   * 등장인물 한 명.
   *
   * @param role 만화 속 호칭 (할머니, 손녀…)
   * @param name 캐릭터 이름 (캐릭터를 못 찾았으면 가명)
   * @param lookId 캐릭터 모습 공개 ID. 못 찾았으면 null
   * @param portraitKey 캐릭터를 못 찾은 인물의 얼굴 그림 객체 키 (만화 컷에서 잘라 낸 것). 없으면 null
   */
  record CastMember(String role, String name, String lookId, String portraitKey) {}

  /** 등록 결과. */
  record Registered(String comicId) {}

  @PostExchange("/internal/comics")
  Registered register(@RequestBody RegisterComic request);

  /** 대화로 찾은 만화에 제작 정보를 채운다. 이미 있으면 그대로 둔다. 만화가 없으면 404. */
  @PutExchange("/internal/comics/by-conversation/{conversationId}/production")
  void production(@PathVariable String conversationId, @RequestBody JsonNode production);

  /** 줄거리 요약 본문. */
  record Summary(String summary) {}

  /** 대화로 찾은 만화에 줄거리 요약을 채운다. 이미 있으면 그대로 둔다. 만화가 없으면 404. */
  @PutExchange("/internal/comics/by-conversation/{conversationId}/summary")
  void summary(@PathVariable String conversationId, @RequestBody Summary summary);

  /** 대화로 찾은 만화의 등장인물 소개를 바꾼다 (예전 만화에 얼굴 그림을 채울 때). 만화가 없으면 404. */
  @PutExchange("/internal/comics/by-conversation/{conversationId}/cast")
  void cast(@PathVariable String conversationId, @RequestBody List<CastMember> cast);
}
