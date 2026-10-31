package net.java21.fourcut.conversation.pipeline;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GpuJobRepository extends JpaRepository<GpuJob, UUID> {

  /** 대기 중인 작업 한 건을 잠근다. 워커가 올려 둔 모델과 같은 종류를 먼저, 그 다음은 들어온 순서. 다른 워커가 잠근 행은 건너뛴다(SKIP LOCKED). */
  @Query(
      value =
          "select * from {h-schema}gpu_jobs where state = 'QUEUED' and type in (:types)"
              + " order by case when type = :preferred then 0 else 1 end, enqueued_at"
              + " limit 1 for update skip locked",
      nativeQuery = true)
  Optional<GpuJob> lockNext(Collection<String> types, String preferred);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from GpuJob j where j.id = ?1")
  Optional<GpuJob> lockById(UUID id);

  /** 임대 시간이 지났거나 마감을 넘긴 작업. 다른 처리 중인 행은 건너뛴다. */
  @Query(
      value =
          "select * from {h-schema}gpu_jobs where state = 'LEASED'"
              + " and (lease_expires_at < :now or deadline_at < :now)"
              + " for update skip locked",
      nativeQuery = true)
  List<GpuJob> lockExpired(Instant now);

  boolean existsByConversationIdAndState(UUID conversationId, GpuJob.State state);
}
