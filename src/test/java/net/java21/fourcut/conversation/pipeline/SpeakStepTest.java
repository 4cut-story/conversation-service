package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class SpeakStepTest {

  private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

  private final PipelineStepRepository steps = mock(PipelineStepRepository.class);
  private final GpuJobRepository jobs = mock(GpuJobRepository.class);
  private final PipelineObserver observer = mock(PipelineObserver.class);
  private final StepHandler compose = mock(StepHandler.class);
  private final StepHandler speak = mock(StepHandler.class);
  private final ConversationInfo conversation =
      new ConversationInfo(1L, "c1", "u", "ko", 1, null, null, false);

  private PipelineService service() {
    when(compose.step()).thenReturn(Step.COMPOSE);
    when(speak.step()).thenReturn(Step.SPEAK);
    when(compose.start(any())).thenReturn(new StepOutcome.Done("k", null));
    when(steps.save(any())).thenAnswer(i -> i.getArgument(0));
    when(observer.find(1L)).thenReturn(conversation);
    return new PipelineService(
        List.of(compose, speak),
        steps,
        jobs,
        mock(GpuWorkerRepository.class),
        observer,
        new PipelineProperties(
            Duration.ofSeconds(90),
            Duration.ofSeconds(60),
            Duration.ofMinutes(8),
            true,
            Duration.ofMinutes(10)),
        JsonMapper.builder().build(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        Runnable::run,
        mock(TransactionTemplate.class),
        mock(ApplicationEventPublisher.class),
        mock(PartTranscriber.class));
  }

  @Test
  @DisplayName("FR-050: 대사 읽기는 한 번만 시도한다")
  void once() {
    assertThat(PipelineService.maxAttempts(Step.SPEAK)).isEqualTo(1);
    assertThat(PipelineService.userMessage(Step.SPEAK)).isNotBlank();
  }

  @Test
  @DisplayName("FR-050: 대사 읽기가 시간을 넘기면 기다리던 작업을 취소하고, 대화를 실패시키지 않고 소리 없이 합성으로 넘어간다")
  void timeoutGoesToCompose() {
    PipelineService service = service();
    PipelineStep running = new PipelineStep(1L, Step.SPEAK, 1, NOW.minus(Duration.ofMinutes(11)));
    GpuJob queued = new GpuJob(1L, Step.SPEAK, JobType.SPEECH, "{}", NOW);
    when(steps.runningSince(eq(Step.SPEAK), any())).thenReturn(List.of(running));
    when(jobs.findByConversationIdAndStepAndState(1L, Step.SPEAK, GpuJob.State.QUEUED))
        .thenReturn(List.of(queued));

    service.recoverLocalSteps();

    assertThat(running.getStatus()).isEqualTo(PipelineStep.Status.FAILED);
    assertThat(queued.getState()).isEqualTo(GpuJob.State.FAILED);
    verify(compose).start(any());
    verify(observer, never()).failed(any(), any());
  }
}
