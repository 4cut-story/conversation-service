package net.java21.fourcut.conversation.comic;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PipelineObserver;
import net.java21.fourcut.conversation.pipeline.Production;
import org.slf4j.Logger;
import org.springframework.web.client.HttpClientErrorException;

/** 합성까지 끝난 대화마다 예전 만화를 채우는 공통 반복. 하나가 실패해도 나머지는 한다. */
final class ComposedBackfill {

  private ComposedBackfill() {}

  /**
   * @param what 로그에 쓰는 채우는 것 이름 (예: "줄거리 요약")
   * @param fill 대화 공개 ID로 한 만화를 채운다. 보냈으면 true
   * @param gone content-service가 404(지운 만화)를 돌려준 대화. 다시 묻지 않게 표시한다
   * @return 보낸 만화 수
   */
  static int run(
      Production production,
      PipelineObserver conversations,
      Logger log,
      String what,
      Predicate<String> fill,
      Consumer<String> gone) {
    List<Long> ids = production.composed();
    int sent = 0;
    for (Long id : ids) {
      ConversationInfo conversation = conversations.find(id);
      if (conversation == null) {
        continue;
      }
      String publicId = conversation.publicId();
      try {
        if (fill.test(publicId)) {
          sent++;
        }
      } catch (HttpClientErrorException.NotFound e) {
        log.debug("지운 만화라 건너뜀: conversation={}", publicId);
        gone.accept(publicId);
      } catch (RuntimeException e) {
        log.warn("{} 채우기 실패: conversation={} ({})", what, publicId, e.toString());
      }
    }
    log.info("{} 채우기: 대화 {}건 중 {}건 보냄", what, ids.size(), sent);
    return sent;
  }
}
