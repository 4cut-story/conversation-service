package net.java21.fourcut.conversation.pipeline;

import org.springframework.context.ApplicationContext;

/** 테스트에서 패키지 안 기능을 부르기 위한 도우미. */
public final class PipelineTestSupport {

  private PipelineTestSupport() {}

  public static void recover(ApplicationContext context) {
    context.getBean(GpuJobService.class).recover();
  }
}
