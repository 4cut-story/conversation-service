package net.java21.fourcut.conversation.pipeline;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GpuWorkerRepository extends JpaRepository<GpuWorker, UUID> {

  Optional<GpuWorker> findByWorkerId(String workerId);

  boolean existsByLastSeenAtAfter(Instant since);
}
