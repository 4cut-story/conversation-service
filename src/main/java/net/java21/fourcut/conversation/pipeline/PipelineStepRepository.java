package net.java21.fourcut.conversation.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PipelineStepRepository extends JpaRepository<PipelineStep, Long> {

  Optional<PipelineStep> findFirstByConversationIdAndStepOrderByAttemptDesc(
      Long conversationId, Step step);

  Optional<PipelineStep> findFirstByConversationIdAndStepOrderByAttemptAsc(
      Long conversationId, Step step);

  boolean existsByConversationIdAndStep(Long conversationId, Step step);

  List<PipelineStep> findByConversationIdOrderByIdAsc(Long conversationId);

  /** 합성까지 끝난(만화가 있는) 대화. */
  @Query(
      "select distinct s.conversationId from PipelineStep s where s.step ="
          + " net.java21.fourcut.conversation.pipeline.Step.COMPOSE and s.status ="
          + " net.java21.fourcut.conversation.pipeline.PipelineStep.Status.SUCCEEDED")
  List<Long> composedConversations();

  @Query(
      "select coalesce(sum(s.attempt - 1), 0) from PipelineStep s where s.conversationId = ?1"
          + " and s.attempt = (select max(s2.attempt) from PipelineStep s2"
          + " where s2.conversationId = s.conversationId and s2.step = s.step)")
  long retries(Long conversationId);

  @Query(
      "select s from PipelineStep s where s.status = net.java21.fourcut.conversation.pipeline"
          + ".PipelineStep.Status.RUNNING and s.step = ?1 and s.startedAt < ?2")
  List<PipelineStep> runningSince(Step step, Instant before);
}
