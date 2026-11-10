package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** GPU 대기열에서 워커에게 줄 다음 작업 고르기. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "voices.enabled=false",
      "DB_SCHEMA=public"
    })
@Import(TestcontainersConfiguration.class)
@Transactional
class GpuJobQueueIntegrationTest {

  @Autowired private GpuJobRepository jobs;

  private static final Instant T = Instant.parse("2026-11-10T12:00:00Z");

  @Test
  @DisplayName("FR-050: 낮은 순위로 들어간 대사 읽기도 먼저 들어온 보통 그림보다 먼저 준다")
  void speechBeforeImagesWhateverPriority() {
    jobs.deleteAll();
    jobs.saveAndFlush(new GpuJob(9001L, Step.DRAW, JobType.IMAGE, "{}", T, GpuJob.NORMAL));
    GpuJob speech =
        jobs.saveAndFlush(
            new GpuJob(9002L, Step.SPEAK, JobType.SPEECH, "{}", T.plusSeconds(60), GpuJob.LOW));
    assertThat(jobs.lockNext(List.of("IMAGE", "SPEECH"), List.of("IMAGE", "SPEECH")))
        .map(GpuJob::getId)
        .contains(speech.getId());
  }

  @Test
  @DisplayName("RSS 구독 만화의 낮은 순위 그림은 나중에 들어온 보통 그림 뒤로 간다")
  void lowImagesWait() {
    jobs.deleteAll();
    jobs.saveAndFlush(new GpuJob(9003L, Step.DRAW, JobType.IMAGE, "{}", T, GpuJob.LOW));
    GpuJob normal =
        jobs.saveAndFlush(
            new GpuJob(9004L, Step.DRAW, JobType.IMAGE, "{}", T.plusSeconds(60), GpuJob.NORMAL));
    assertThat(jobs.lockNext(List.of("IMAGE", "SPEECH"), List.of("")))
        .map(GpuJob::getId)
        .contains(normal.getId());
  }
}
