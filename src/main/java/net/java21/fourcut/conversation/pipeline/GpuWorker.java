package net.java21.fourcut.conversation.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import net.java21.fourcut.conversation.support.BaseEntity;

/** GPU 워커 상태. claim과 heartbeat 때 갱신한다. */
@Entity
@Table(name = "gpu_workers")
public class GpuWorker extends BaseEntity {

  @Column(name = "worker_id", nullable = false, length = 50)
  private String workerId;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "loaded_model", length = 20)
  private String loadedModel;

  @Column(length = 50)
  private String version;

  protected GpuWorker() {
    // JPA 전용
  }

  public GpuWorker(String workerId, Instant now) {
    this.workerId = workerId;
    this.lastSeenAt = now;
  }

  public String getWorkerId() {
    return workerId;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public String getLoadedModel() {
    return loadedModel;
  }

  void seen(Instant now, String loadedModel, String version) {
    this.lastSeenAt = now;
    if (loadedModel != null) {
      this.loadedModel = loadedModel;
    }
    if (version != null) {
      this.version = PipelineStep.truncate(version, 50);
    }
  }

  void seen(Instant now) {
    this.lastSeenAt = now;
  }
}
