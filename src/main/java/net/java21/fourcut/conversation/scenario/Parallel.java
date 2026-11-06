package net.java21.fourcut.conversation.scenario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 서로 기대지 않는 외부 LLM 호출 여러 개를 동시에 한다. 한 번에 {@code concurrency}개까지만 부르고(API 한도), {@code timeout} 안에
 * 끝나지 않은 호출은 취소한다. 결과는 넣은 순서대로이고, 실패·시간 초과는 빈 값이다.
 */
final class Parallel {

  private static final Logger log = LoggerFactory.getLogger(Parallel.class);

  private Parallel() {}

  static <T> List<Optional<T>> run(List<Callable<T>> tasks, int concurrency, Duration timeout) {
    if (tasks.isEmpty()) {
      return List.of();
    }
    Semaphore permits = new Semaphore(Math.max(1, concurrency));
    List<Callable<T>> limited = new ArrayList<>(tasks.size());
    for (Callable<T> task : tasks) {
      limited.add(
          () -> {
            permits.acquire();
            try {
              return task.call();
            } finally {
              permits.release();
            }
          });
    }
    List<Optional<T>> out = new ArrayList<>(tasks.size());
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<T>> futures =
          executor.invokeAll(limited, timeout.toMillis(), TimeUnit.MILLISECONDS);
      for (Future<T> f : futures) {
        out.add(result(f));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      while (out.size() < tasks.size()) {
        out.add(Optional.empty());
      }
    }
    return out;
  }

  private static <T> Optional<T> result(Future<T> future) throws InterruptedException {
    try {
      return Optional.ofNullable(future.get());
    } catch (CancellationException e) {
      log.warn("동시 호출 시간 초과로 취소");
      return Optional.empty();
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() == null ? e : e.getCause();
      log.warn("동시 호출 실패: {}", cause.getClass().getSimpleName() + ": " + cause.getMessage());
      return Optional.empty();
    }
  }
}
