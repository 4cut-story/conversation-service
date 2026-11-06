package net.java21.fourcut.conversation.comic;

import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/** content-service 내부 API. 같은 대화로 다시 보내면 기존 만화 ID를 돌려준다(멱등). */
public interface ContentClient {

  /**
   * 만화 등록 요청.
   *
   * @param cast 만화 아래에 보이는 등장인물 소개
   * @param concept 그린 그림체({@code Concept} 이름). 자동이면 서버가 고른 그림체
   * @param dialect 대사 사투리({@code Dialect} 이름). 사투리 없음은 NONE
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
      String dialect) {}

  /**
   * 등장인물 한 명.
   *
   * @param role 만화 속 호칭 (할머니, 손녀…)
   * @param name 캐릭터 이름 (캐릭터를 못 찾았으면 가명)
   * @param lookId 캐릭터 모습 공개 ID. 못 찾았으면 null
   */
  record CastMember(String role, String name, String lookId) {}

  /** 등록 결과. */
  record Registered(String comicId) {}

  @PostExchange("/internal/comics")
  Registered register(@RequestBody RegisterComic request);
}
