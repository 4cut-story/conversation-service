package net.java21.fourcut.conversation.pipeline;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 테스트 전용 수동 LLM 처리. 수동 처리 대화의 LLM 작업(장면 고르기·콘티)은 서버(Claude·Gemini)와 GPU 워커가 가져가지 않고, 대화 주인이 서버가 만든
 * 프롬프트를 그대로 꺼내 답을 넣는다. 답을 넣은 뒤의 후처리·그림·합성은 서비스와 같다.
 */
@Component
public class ManualLlmJobs {

  private final GpuJobService jobs;

  ManualLlmJobs(GpuJobService jobs) {
    this.jobs = jobs;
  }

  /** 답을 기다리는 작업. */
  public record Job(Long jobId, String step, String payload) {}

  /** 대화의 답을 기다리는 수동 LLM 작업. */
  public List<Job> waiting(Long conversationId) {
    return jobs.manualJobs(conversationId).stream()
        .map(j -> new Job(j.jobId(), j.step().name(), j.payload()))
        .toList();
  }

  /**
   * 답을 넣는다.
   *
   * @return 그 대화의 기다리는 수동 작업이 아니면 false
   */
  public boolean answer(Long conversationId, Long jobId, String content) {
    return jobs.answerManual(conversationId, jobId, content);
  }
}
