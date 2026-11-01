package net.java21.fourcut.conversation.comic;

import java.util.List;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

/** content-service 내부 API. 같은 대화로 다시 보내면 기존 만화 ID를 돌려준다(멱등). */
public interface ContentClient {

  /** 만화 등록 요청. */
  record RegisterComic(
      String ownerId,
      String conversationId,
      String title,
      String imageKey,
      String thumbKey,
      String language,
      List<String> nameWarnings) {}

  /** 등록 결과. */
  record Registered(String comicId) {}

  @PostExchange("/internal/comics")
  Registered register(@RequestBody RegisterComic request);
}
