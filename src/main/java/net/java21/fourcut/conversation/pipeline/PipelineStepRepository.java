package net.java21.fourcut.conversation.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface PipelineStepRepository extends JpaRepository<PipelineStep, UUID> {

  Optional<PipelineStep> findFirstByConversationIdAndStepOrderByAttemptDesc(
      UUID conversationId, Step step);

  Optional<PipelineStep> findFirstByConversationIdAndStepOrderByAttemptAsc(
      UUID conversationId, Step step);

  @Query(
      "select coalesce(sum(s.attempt - 1), 0) from PipelineStep s where s.conversationId = ?1"
          + " and s.attempt = (select max(s2.attempt) from PipelineStep s2"
          + " where s2.conversationId = s.conversationId and s2.step = s.step)")
  long retries(UUID conversationId);

  @Query(
      "select s from PipelineStep s where s.status = net.java21.fourcut.conversation.pipeline"
          + ".PipelineStep.Status.RUNNING and s.step = ?1 and s.startedAt < ?2")
  List<PipelineStep> runningSince(Step step, Instant before);
}
