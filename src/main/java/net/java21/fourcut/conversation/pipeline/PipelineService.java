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
import org.springframework.context.ApplicationEventPublisher;
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

  /**
   * 콘티는 LLM 출력이 규칙(반복·화자·대사 수)을 어기면 다시 쓰는데, 세 번 안에 못 넘겨 만화가 실패하는 일이 열 몇 편에 두 번 나왔다. 콘티 한 번은 짧으므로 다섯
   * 번까지 쓴다.
   */
  static final int SCRIPT_MAX_ATTEMPTS = 5;

  public static int maxAttempts(Step step) {
    return switch (step) {
      case SCRIPT -> SCRIPT_MAX_ATTEMPTS;
      // 대사 음성이 없어도 만화는 나온다. 다시 하지 않고 소리 없이 합성으로 넘어간다
      case SPEAK -> 1;
      default -> MAX_ATTEMPTS;
    };
  }

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
  private final ApplicationEventPublisher events;
  private final PartTranscriber parts;

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
      TransactionTemplate tx,
      ApplicationEventPublisher events,
      PartTranscriber parts) {
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
    this.events = events;
    this.parts = parts;
  }

  /** 녹음이 끝난 대화의 처리를 시작한다 (전사부터). 아직 가져가지 않은 구간 전사는 취소한다. */
  @Transactional
  public void start(Long conversationId) {
    ConversationInfo conversation = observer.find(conversationId);
    if (conversation != null) {
      jobs.findByConversationIdAndStepAndState(
              conversationId, Step.TRANSCRIBE_PART, GpuJob.State.QUEUED)
          .forEach(j -> j.fail("CANCELLED", "녹음이 끝나 마무리 전사로 넘겼다", now()));
      startStep(conversation, Step.TRANSCRIBE, 1);
    }
  }

  /**
   * 녹음 조각이 올라왔다 (FR-015). 진행 중인 구간 전사가 없고 새 조각이 있으면 낮은 우선순위로 구간 전사를 넣는다. 구간 전사는 한 대화에 한 번에 하나만 돈다.
   *
   * @param uploadedChunks 다 올라온 조각 수
   */
  @Transactional
  public void chunkUploaded(Long conversationId, int uploadedChunks) {
    if (!properties.preTranscribe() || uploadedChunks <= 0) {
      return;
    }
    if (jobs.existsByConversationIdAndStepAndStateIn(
        conversationId, Step.TRANSCRIBE_PART, List.of(GpuJob.State.QUEUED, GpuJob.State.LEASED))) {
      return;
    }
    ConversationInfo conversation = observer.find(conversationId);
    if (conversation == null) {
      return;
    }
    var payload = parts.partPayload(conversation, uploadedChunks);
    if (payload != null) {
      jobs.saveAndFlush(
          new GpuJob(
              conversationId,
              Step.TRANSCRIBE_PART,
              JobType.TRANSCRIBE,
              json.writeValueAsString(payload),
              now(),
              GpuJob.LOW));
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

  /** 이 대화가 그 단계를 한 번이라도 시작했는지. */
  @Transactional(readOnly = true)
  public boolean reached(Long conversationId, Step step) {
    return steps.existsByConversationIdAndStep(conversationId, step);
  }

  /** GPU 작업이 대기 중인데 최근에 연락한 워커가 없는지. 그렇다면 GPU_WAITING으로 보여 준다. */
  @Transactional(readOnly = true)
  public boolean waitingForGpu(Long conversationId) {
    return jobs.existsByConversationIdAndState(conversationId, GpuJob.State.QUEUED)
        && !workers.existsByLastSeenAtAfter(now().minus(properties.workerSeenWithin()));
  }

  void jobSucceeded(GpuJob job, JsonNode result) {
    if (job.getStep() == Step.TRANSCRIBE_PART) {
      ConversationInfo conversation = observer.find(job.getConversationId());
      if (conversation != null) {
        try {
          parts.partDone(conversation, json.readTree(job.getPayload()), result);
        } catch (RuntimeException e) {
          log.warn("구간 전사 결과를 쓰지 못했다: job={}", job.getId(), e);
        }
      }
      return;
    }
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
    if (job.getStep() == Step.TRANSCRIBE_PART) {
      // 구간 전사가 실패해도 대화는 실패하지 않는다. 다음 조각 때 다시 하거나 마무리 전사가 그 부분을 한다
      log.info("구간 전사 실패: job={} code={}", job.getId(), job.getErrorCode());
      return;
    }
    withCurrentStep(
        job,
        (conversation, step) -> {
          String reason = "GPU 작업 실패 " + job.getErrorCode() + ": " + job.getErrorMessage();
          StepContext context =
              new StepContext(
                  conversation, step.getStep(), step.getAttempt(), json.readTree(job.getPayload()));
          apply(
              conversation,
              step,
              safely(() -> handler(step.getStep()).onJobFailed(context, reason)));
        });
  }

  /**
   * 서버 단계(합성, 장면 추출 전 전사 교정)가 너무 오래 RUNNING이면(서버가 재시작된 경우 등) 실패로 보고 다시 한다. 장면 추출은 아직 GPU 작업을 넣지 않은
   * 경우(교정 중)만 본다.
   */
  @Scheduled(fixedDelayString = "${pipeline.local-recovery-interval:60s}")
  @Transactional
  public void recoverLocalSteps() {
    Instant before = now().minus(properties.localStepTimeout());
    List<PipelineStep> stuck = new java.util.ArrayList<>(steps.runningSince(Step.COMPOSE, before));
    steps.runningSince(Step.EXTRACT, before).stream()
        .filter(s -> s.getRuntimeJobId() == null)
        .forEach(stuck::add);
    for (PipelineStep step : stuck) {
      ConversationInfo conversation = observer.find(step.getConversationId());
      if (conversation != null) {
        failStep(conversation, step, "서버 단계 시간 초과");
      }
    }
    // 그림 결과를 받은 뒤 서버 작업(움직임 그림 정하기, FR-051)이 끝나지 않은 그리기 단계. 그림을 다시 그리지 않게 처리기가 정한다
    for (PipelineStep step : steps.runningSince(Step.DRAW, before)) {
      ConversationInfo conversation = observer.find(step.getConversationId());
      GpuJob job = finishedJob(step);
      if (conversation != null
          && job != null
          && job.getFinishedAt() != null
          && job.getFinishedAt().isBefore(before)) {
        StepContext context =
            new StepContext(
                conversation, step.getStep(), step.getAttempt(), json.readTree(job.getPayload()));
        apply(conversation, step, safely(() -> handler(step.getStep()).onStalled(context)));
      }
    }
    // 대사 읽기는 워커가 가져가지 않으면(대사 읽기를 끈 워커) 기다리지 않고 소리 없이 넘어간다
    for (PipelineStep step :
        steps.runningSince(Step.SPEAK, now().minus(properties.speechTimeout()))) {
      ConversationInfo conversation = observer.find(step.getConversationId());
      if (conversation == null) {
        continue;
      }
      jobs.findByConversationIdAndStepAndState(conversation.id(), Step.SPEAK, GpuJob.State.QUEUED)
          .forEach(j -> j.fail("CANCELLED", "대사 읽기를 가져간 워커가 없다", now()));
      failStep(conversation, step, "대사 읽기 시간 초과");
    }
  }

  /** 단계가 마지막으로 넣은 GPU 작업이 이미 성공했으면 그 작업. 아니면 null. */
  private GpuJob finishedJob(PipelineStep step) {
    if (step.getRuntimeJobId() == null) {
      return null;
    }
    try {
      return jobs.findById(Long.valueOf(step.getRuntimeJobId()))
          .filter(j -> j.getState() == GpuJob.State.SUCCEEDED)
          .orElse(null);
    } catch (NumberFormatException e) {
      return null;
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
        GpuJob created =
            new GpuJob(
                conversation.id(),
                step.getStep(),
                e.type(),
                json.writeValueAsString(e.payload()),
                now(),
                priorityOf(conversation, e.type()));
        boolean manual = e.type() == JobType.LLM && conversation.manualLlm();
        if (manual) {
          created.markManual();
        }
        GpuJob job = jobs.saveAndFlush(created);
        step.jobEnqueued(job.getId());
        if (e.modelInfo() != null) {
          step.modelInfo(e.modelInfo());
        }
        if (e.type() == JobType.LLM && !manual) {
          events.publishEvent(new LlmJobQueued(job.getId()));
        }
      }
      case StepOutcome.RunLocal r -> afterCommit(step.getId());
      case StepOutcome.Done d -> {
        step.succeed(d.resultKey(), now());
        logDone(step);
        if (d.next() != null) {
          startStep(conversation, d.next(), 1);
        }
      }
      case StepOutcome.Completed c -> {
        step.succeed(c.resultKey(), now());
        logDone(step);
        Instant began =
            steps
                .findFirstByConversationIdAndStepOrderByAttemptAsc(
                    conversation.id(), Step.TRANSCRIBE)
                .map(PipelineStep::getStartedAt)
                .orElse(now());
        int total = (int) java.time.Duration.between(began, now()).toSeconds();
        log.info("만화 완성: conversation={} 전사 시작부터 {}초", conversation.id(), total);
        observer.completed(conversation.id(), c.comicId(), total);
      }
      case StepOutcome.NoScene n -> {
        step.succeed(n.resultKey(), now());
        observer.noScene(conversation.id());
      }
      case StepOutcome.Retry r -> failStep(conversation, step, r.reason());
    }
  }

  /** 단계별 소요 시간. 어디가 느린지 로그로 바로 보이게 한다. */
  private void logDone(PipelineStep step) {
    log.info(
        "단계 완료: conversation={} step={} attempt={} {}ms",
        step.getConversationId(),
        step.getStep(),
        step.getAttempt(),
        java.time.Duration.between(step.getStartedAt(), now()).toMillis());
  }

  private void failStep(ConversationInfo conversation, PipelineStep step, String reason) {
    log.warn(
        "단계 실패: conversation={} step={} attempt={} reason={}",
        conversation.id(),
        step.getStep(),
        step.getAttempt(),
        reason);
    step.fail(reason, now());
    if (step.getStep() == Step.SPEAK) {
      startStep(conversation, Step.COMPOSE, 1);
      return;
    }
    if (step.getAttempt() < maxAttempts(step.getStep())) {
      startStep(conversation, step.getStep(), step.getAttempt() + 1);
    } else {
      observer.failed(
          conversation.id(),
          conversation.article() != null && step.getStep() == Step.TRANSCRIBE
              ? ARTICLE_FAILED
              : userMessage(step.getStep()));
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

  /**
   * GPU 작업 우선순위. RSS 구독이 한꺼번에 만든 만화(뉴스 수십 편)는 사람이 만든 만화 뒤로 미룬다. 같은 차례면 먼저 넣은 작업부터라, 뉴스가 몰리면 사람이 만든
   * 만화가 그 뒤에서 수십 분을 기다렸다.
   */
  static short priorityOf(ConversationInfo conversation, JobType type) {
    // 대사 읽기는 몇 초면 끝나는 마지막 GPU 작업이다. 낮은 순위로 두면 그림 뒤에서 기다리다 시간 초과로 소리 없이 넘어간다
    if (type == JobType.SPEECH) {
      return GpuJob.NORMAL;
    }
    return conversation.fromFeed() ? GpuJob.LOW : GpuJob.NORMAL;
  }

  /** 기사를 대화로 각색하지 못했을 때 사용자에게 보여 줄 문장. */
  static final String ARTICLE_FAILED = "기사를 만화 이야기로 바꾸지 못했어요. 잠시 뒤 다시 만들어 주세요.";

  static String userMessage(Step step) {
    return switch (step) {
      case TRANSCRIBE, TRANSCRIBE_PART -> "음성을 글로 바꾸지 못했어요. 다시 녹음해 주세요.";
      case EXTRACT, SELECT -> "대화에서 장면을 고르지 못했어요.";
      case SCRIPT, REVIEW -> "콘티를 만들지 못했어요.";
      case DRAW -> "그림을 그리지 못했어요.";
      case SPEAK -> "대사를 읽지 못했어요.";
      case COMPOSE -> "만화를 합치지 못했어요.";
    };
  }

  @FunctionalInterface
  private interface StepAction {
    void run(ConversationInfo conversation, PipelineStep step);
  }
}
