package net.java21.fourcut.conversation.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 파이프라인 엔진. 단계를 순서대로 시작하고, GPU 작업 결과와 실패를 받아 다음 단계로 넘기거나 같은 단계를 다시 한다 (단계마다 최대 3번 시도). */
@Service
public class PipelineService {

  /** 단계마다 처음 1번 + 재시도 2번. */
  public static final int MAX_ATTEMPTS = 3;

  private static final Logger log = LoggerFactory.getLogger(PipelineService.class);

  private final Map<Step, StepHandler> handlers = new EnumMap<>(Step.class);
  private final PipelineStepRepository steps;
  private final GpuJobRepository jobs;
  private final GpuWorkerRepository workers;
  private final PipelineObserver observer;
  private final PipelineProperties properties;
  private final JsonMapper json;
  private final Clock clock;
  private final TaskExecutor executor;
  private final TransactionTemplate tx;

  public PipelineService(
      List<StepHandler> handlerList,
      PipelineStepRepository steps,
      GpuJobRepository jobs,
      GpuWorkerRepository workers,
      PipelineObserver observer,
      PipelineProperties properties,
      JsonMapper json,
      Clock clock,
      @Qualifier("applicationTaskExecutor") TaskExecutor executor,
      TransactionTemplate tx) {
    handlerList.forEach(h -> handlers.put(h.step(), h));
    this.steps = steps;
    this.jobs = jobs;
    this.workers = workers;
    this.observer = observer;
    this.properties = properties;
    this.json = json;
    this.clock = clock;
    this.executor = executor;
    this.tx = tx;
  }

  /** 녹음이 끝난 대화의 처리를 시작한다 (전사부터). */
  @Transactional
  public void start(Long conversationId) {
    ConversationInfo conversation = observer.find(conversationId);
    if (conversation != null) {
      startStep(conversation, Step.TRANSCRIBE, 1);
    }
  }

  /** 대화 정보. 대화가 지워졌으면 null. */
  ConversationInfo conversation(Long conversationId) {
    return observer.find(conversationId);
  }

  /** 지금까지 다시 시도한 횟수 (단계별 마지막 시도 기준 합). */
  @Transactional(readOnly = true)
  public int retries(Long conversationId) {
    return (int) steps.retries(conversationId);
  }

  /** GPU 작업이 대기 중인데 최근에 연락한 워커가 없는지. 그렇다면 GPU_WAITING으로 보여 준다. */
  @Transactional(readOnly = true)
  public boolean waitingForGpu(Long conversationId) {
    return jobs.existsByConversationIdAndState(conversationId, GpuJob.State.QUEUED)
        && !workers.existsByLastSeenAtAfter(now().minus(properties.workerSeenWithin()));
  }

  void jobSucceeded(GpuJob job, JsonNode result) {
    withCurrentStep(
        job,
        (conversation, step) -> {
          StepContext context =
              new StepContext(
                  conversation, step.getStep(), step.getAttempt(), json.readTree(job.getPayload()));
          apply(
              conversation, step, safely(() -> handler(step.getStep()).onResult(context, result)));
        });
  }

  void jobFailed(GpuJob job) {
    withCurrentStep(
        job,
        (conversation, step) ->
            failStep(
                conversation,
                step,
                "GPU 작업 실패 " + job.getErrorCode() + ": " + job.getErrorMessage()));
  }

  /** 서버 단계(합성)가 너무 오래 RUNNING이면(서버가 재시작된 경우 등) 실패로 보고 다시 한다. */
  @Scheduled(fixedDelayString = "${pipeline.local-recovery-interval:60s}")
  @Transactional
  public void recoverLocalSteps() {
    Instant before = now().minus(properties.localStepTimeout());
    for (PipelineStep step : steps.runningSince(Step.COMPOSE, before)) {
      ConversationInfo conversation = observer.find(step.getConversationId());
      if (conversation != null) {
        failStep(conversation, step, "서버 단계 시간 초과");
      }
    }
  }

  private void withCurrentStep(GpuJob job, StepAction action) {
    ConversationInfo conversation = observer.find(job.getConversationId());
    if (conversation == null) {
      return;
    }
    steps
        .findFirstByConversationIdAndStepOrderByAttemptDesc(job.getConversationId(), job.getStep())
        .filter(s -> s.getStatus() == PipelineStep.Status.RUNNING)
        .filter(s -> job.getId().toString().equals(s.getRuntimeJobId()))
        .ifPresent(s -> action.run(conversation, s));
  }

  private void startStep(ConversationInfo conversation, Step step, int attempt) {
    PipelineStep record = steps.save(new PipelineStep(conversation.id(), step, attempt, now()));
    observer.stepStarted(conversation.id(), step);
    StepContext context = new StepContext(conversation, step, attempt, null);
    apply(conversation, record, safely(() -> handler(step).start(context)));
  }

  private void apply(ConversationInfo conversation, PipelineStep step, StepOutcome outcome) {
    switch (outcome) {
      case StepOutcome.Enqueue e -> {
        // 앞 작업의 상태 변경을 먼저 반영해야 "대화당 진행 중 작업 1개" 유니크 인덱스에 걸리지 않는다
        jobs.flush();
        GpuJob job =
            jobs.saveAndFlush(
                new GpuJob(
                    conversation.id(),
                    step.getStep(),
                    e.type(),
                    json.writeValueAsString(e.payload()),
                    now()));
        step.jobEnqueued(job.getId());
        if (e.modelInfo() != null) {
          step.modelInfo(e.modelInfo());
        }
      }
      case StepOutcome.RunLocal r -> afterCommit(step.getId());
      case StepOutcome.Done d -> {
        step.succeed(d.resultKey(), now());
        if (d.next() != null) {
          startStep(conversation, d.next(), 1);
        }
      }
      case StepOutcome.Completed c -> {
        step.succeed(c.resultKey(), now());
        Instant began =
            steps
                .findFirstByConversationIdAndStepOrderByAttemptAsc(
                    conversation.id(), Step.TRANSCRIBE)
                .map(PipelineStep::getStartedAt)
                .orElse(now());
        observer.completed(
            conversation.id(),
            c.comicId(),
            (int) java.time.Duration.between(began, now()).toSeconds());
      }
      case StepOutcome.NoScene n -> {
        step.succeed(n.resultKey(), now());
        observer.noScene(conversation.id());
      }
      case StepOutcome.Retry r -> failStep(conversation, step, r.reason());
    }
  }

  private void failStep(ConversationInfo conversation, PipelineStep step, String reason) {
    log.warn(
        "단계 실패: conversation={} step={} attempt={} reason={}",
        conversation.id(),
        step.getStep(),
        step.getAttempt(),
        reason);
    step.fail(reason, now());
    if (step.getAttempt() < MAX_ATTEMPTS) {
      startStep(conversation, step.getStep(), step.getAttempt() + 1);
    } else {
      observer.failed(conversation.id(), userMessage(step.getStep()));
    }
  }

  private void afterCommit(Long stepId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            executor.execute(() -> runLocal(stepId));
          }
        });
  }

  /** 서버 단계 본 작업. 오래 걸리므로 트랜잭션 밖에서 하고, 결과만 트랜잭션으로 반영한다. */
  void runLocal(Long stepId) {
    StepContext context =
        tx.execute(
            status ->
                steps
                    .findById(stepId)
                    .filter(s -> s.getStatus() == PipelineStep.Status.RUNNING)
                    .map(
                        s -> {
                          ConversationInfo c = observer.find(s.getConversationId());
                          return c == null
                              ? null
                              : new StepContext(c, s.getStep(), s.getAttempt(), null);
                        })
                    .orElse(null));
    if (context == null) {
      return;
    }
    StepOutcome outcome = safely(() -> handler(context.step()).runLocal(context));
    tx.executeWithoutResult(
        status ->
            steps
                .findById(stepId)
                .filter(s -> s.getStatus() == PipelineStep.Status.RUNNING)
                .ifPresent(s -> apply(context.conversation(), s, outcome)));
  }

  private StepOutcome safely(Supplier<StepOutcome> work) {
    try {
      return work.get();
    } catch (RuntimeException e) {
      log.warn("단계 처리 오류", e);
      return new StepOutcome.Retry(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private StepHandler handler(Step step) {
    StepHandler handler = handlers.get(step);
    if (handler == null) {
      throw new IllegalStateException("단계 처리기가 없다: " + step);
    }
    return handler;
  }

  private Instant now() {
    return clock.instant();
  }

  static String userMessage(Step step) {
    return switch (step) {
      case TRANSCRIBE -> "음성을 글로 바꾸지 못했어요. 다시 녹음해 주세요.";
      case EXTRACT, SELECT -> "대화에서 장면을 고르지 못했어요.";
      case SCRIPT, REVIEW -> "콘티를 만들지 못했어요.";
      case DRAW -> "그림을 그리지 못했어요.";
      case COMPOSE -> "만화를 합치지 못했어요.";
    };
  }

  @FunctionalInterface
  private interface StepAction {
    void run(ConversationInfo conversation, PipelineStep step);
  }
}
