package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;

/** GPU 작업 종류. 종류마다 최대 실행 시간과 워커가 올리는 모델 묶음이 정해져 있다. */
public enum JobType {
  TRANSCRIBE(Duration.ofMinutes(20), "WHISPER"),
  LLM(Duration.ofMinutes(5), "LLM"),
  IMAGE(Duration.ofMinutes(10), "SDXL");

  private final Duration maxRun;
  private final String model;

  JobType(Duration maxRun, String model) {
    this.maxRun = maxRun;
    this.model = model;
  }

  public Duration maxRun() {
    return maxRun;
  }

  /** 이 작업에 쓰는 모델 묶음 ({@code loadedModel} 값). */
  public String model() {
    return model;
  }

  /** 워커가 올려 둔 모델 묶음에 맞는 작업 종류. 모르면 null. */
  public static JobType forModel(String loadedModel) {
    for (JobType t : values()) {
      if (t.model.equals(loadedModel)) {
        return t;
      }
    }
    return null;
  }
}
