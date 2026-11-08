package net.java21.fourcut.conversation.comic;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.Production;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 제작 정보가 생기기 전에 만든 만화를 남은 단계 기록으로 채운다. 서버가 뜰 때 한 번 뒤에서 돈다. content-service는 비어 있는 만화만 채우므로 여러 번 돌아도
 * 같다. 예전 그림 작업은 워커가 보낸 모델 정보를 버렸으므로 걸린 시간과 처리한 쪽만 남는다.
 */
@Component
class ProductionBackfill {

  private static final Logger log = LoggerFactory.getLogger(ProductionBackfill.class);

  private final Production production;
  private final ContentClient content;
  private final boolean enabled;

  ProductionBackfill(
      Production production,
      ContentClient content,
      @Value("${comic.production-backfill:false}") boolean enabled) {
    this.production = production;
    this.content = content;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    if (enabled) {
      Thread.ofVirtual().name("production-backfill").start(this::run);
    }
  }

  /** 합성까지 끝난 대화마다 제작 정보를 만들어 보낸다. 하나가 실패해도 나머지는 한다. */
  int run() {
    List<Long> ids = production.composed();
    int sent = 0;
    for (Long id : ids) {
      try {
        Optional<Production.Built> built = production.of(id, true);
        if (built.isPresent()) {
          content.production(built.get().conversationId(), built.get().production());
          sent++;
        }
      } catch (HttpClientErrorException.NotFound e) {
        // 지운 만화
      } catch (RuntimeException e) {
        log.warn("제작 정보 채우기 실패: conversation={} ({})", id, e.getMessage());
      }
    }
    log.info("제작 정보 채우기: 대화 {}건 중 {}건 보냄", ids.size(), sent);
    return sent;
  }
}
