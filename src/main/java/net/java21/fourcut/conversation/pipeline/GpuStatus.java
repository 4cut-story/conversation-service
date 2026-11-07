package net.java21.fourcut.conversation.pipeline;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GPU PC 워커가 최근에 연락했는지. 녹음 화면이 녹음 전에 "지금은 늦게 만들어져요"를 알려 줄 때 쓴다. */
@Service
public class GpuStatus {

  private final GpuWorkerRepository workers;
  private final PipelineProperties properties;
  private final Clock clock;

  GpuStatus(GpuWorkerRepository workers, PipelineProperties properties, Clock clock) {
    this.workers = workers;
    this.properties = properties;
    this.clock = clock;
  }

  /** 최근({@code pipeline.worker-seen-within}) 안에 연락한 워커가 있으면 true. */
  @Transactional(readOnly = true)
  public boolean online() {
    return workers.existsByLastSeenAtAfter(clock.instant().minus(properties.workerSeenWithin()));
  }
}
