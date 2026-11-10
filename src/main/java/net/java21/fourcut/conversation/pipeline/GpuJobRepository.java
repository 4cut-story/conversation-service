package net.java21.fourcut.conversation.pipeline;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GpuJobRepository extends JpaRepository<GpuJob, Long> {

  /** 손으로 넣지 않은 작업 가운데 이 종류·상태인 것. */
  List<GpuJob> findByTypeAndManualFalseAndStateIn(JobType type, Collection<GpuJob.State> states);

  /**
   * 대기 중인 작업 한 건을 잠근다. 보통 작업을 낮은 우선순위(녹음 중 구간 전사)보다 먼저, 그 안에서는 워커가 올려 둔 모델 묶음을 쓰는 종류(preferred)를
   * 먼저, 그 안에서는 대사 읽기(SPEECH)를 먼저, 그 다음은 들어온 순서. 대사 읽기는 몇 초면 끝나고 그림이 끝난 만화를 완성하는 마지막 GPU 작업이라, 다른
   * 만화의 그림(몇 분)보다 먼저 준다. 다른 워커가 잠근 행은 건너뛴다(SKIP LOCKED).
   */
  @Query(
      value =
          "select * from {h-schema}gpu_jobs where state = 'QUEUED' and manual = false"
              + " and type in (:types)"
              + " order by priority, case when type in (:preferred) then 0 else 1 end,"
              + " case when type = 'SPEECH' then 0 else 1 end, enqueued_at"
              + " limit 1 for update skip locked",
      nativeQuery = true)
  Optional<GpuJob> lockNext(Collection<String> types, Collection<String> preferred);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from GpuJob j where j.id = ?1")
  Optional<GpuJob> lockById(Long id);

  /** 임대 시간이 지났거나 마감을 넘긴 작업. 다른 처리 중인 행은 건너뛴다. */
  @Query(
      value =
          "select * from {h-schema}gpu_jobs where state = 'LEASED'"
              + " and (lease_expires_at < :now or deadline_at < :now)"
              + " for update skip locked",
      nativeQuery = true)
  List<GpuJob> lockExpired(Instant now);

  List<GpuJob> findByConversationIdAndStateAndManualTrue(Long conversationId, GpuJob.State state);

  boolean existsByConversationIdAndState(Long conversationId, GpuJob.State state);

  boolean existsByConversationIdAndStepAndStateIn(
      Long conversationId, Step step, Collection<GpuJob.State> states);

  List<GpuJob> findByConversationIdAndStepAndState(
      Long conversationId, Step step, GpuJob.State state);

  List<GpuJob> findByConversationIdAndStateOrderByIdAsc(Long conversationId, GpuJob.State state);
}
