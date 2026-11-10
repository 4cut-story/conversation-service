package net.java21.fourcut.conversation.article;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 기사 URL을 받아 제목과 본문을 돌려준다. 받기와 골라내기 전체를 시간 한도 안에 끝낸다. */
@Component
public class ArticleReader {

  private static final Logger log = LoggerFactory.getLogger(ArticleReader.class);

  private final ArticleFetcher fetcher;
  private final ArticleProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  ArticleReader(ArticleFetcher fetcher, ArticleProperties properties) {
    this.fetcher = fetcher;
    this.properties = properties;
  }

  /**
   * 기사를 읽는다.
   *
   * @throws ArticleException 열 수 없는 주소, 받기 실패, HTML이 아님, 본문 없음
   */
  public Article read(String url) {
    return within(() -> readNow(url));
  }

  /**
   * RSS·Atom 피드 문서를 기사와 같은 규칙(내부망 차단, 넘겨주기마다 검사, 크기·시간 한도)으로 받는다. 피드인지는 받은 쪽이 읽으며 가린다.
   *
   * @throws ArticleException 열 수 없는 주소, 받기 실패, 글이 아닌 응답(NOT_FEED)
   */
  public FeedDocument fetchFeed(String url) {
    return within(
        () -> {
          ArticleFetcher.Page page = fetcher.fetch(url, ArticleFetcher.Kind.FEED);
          return new FeedDocument(page.uri().toString(), page.body());
        });
  }

  private <T> T within(java.util.concurrent.Callable<T> task) {
    Future<T> work = executor.submit(task);
    try {
      // 응답을 아주 느리게 흘려보내는 서버에도 붙잡히지 않게 전체 시간을 따로 잰다
      return work.get(properties.timeout().toMillis() + 5_000, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      work.cancel(true);
      throw new ArticleException(ArticleException.Reason.UNREACHABLE, "timeout", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ArticleException a) {
        log.info("기사를 읽지 못했다: reason={} {}", a.reason(), a.getMessage());
        throw new ArticleException(a.reason(), null, e);
      }
      log.warn("기사 읽기 오류", e.getCause());
      throw new ArticleException(ArticleException.Reason.UNREACHABLE, "error", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ArticleException(ArticleException.Reason.UNREACHABLE, "interrupted", e);
    }
  }

  private Article readNow(String url) {
    ArticleFetcher.Page page = fetcher.fetch(url);
    Article article =
        ArticleExtractor.extract(page.uri().toString(), page.uri().getHost(), page.body());
    log.info(
        "기사 읽음: host={} 본문 {}자 언어={}",
        page.uri().getHost(),
        article.text().length(),
        article.language());
    return article;
  }
}
