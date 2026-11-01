package net.java21.fourcut.conversation.pipeline;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GpuWorkerRepository extends JpaRepository<GpuWorker, Long> {

  Optional<GpuWorker> findByWorkerId(String workerId);

  boolean existsByLastSeenAtAfter(Instant since);
}
