package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;

/** GPU 작업 종류. 종류마다 최대 실행 시간과 워커가 올리는 모델 묶음이 정해져 있다. */
public enum JobType {
  TRANSCRIBE(Duration.ofMinutes(20), "WHISPER"),
  LLM(Duration.ofMinutes(5), "LLM"),
  IMAGE(Duration.ofMinutes(10), "SDXL"),
  /** 대사 읽기(GPT-SoVITS v4). 그림 모델과 함께 올려 두므로 모델 묶음이 같다. */
  SPEECH(Duration.ofMinutes(5), "SDXL");

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

  /** 워커가 올려 둔 모델 묶음을 쓰는 모든 작업 종류 이름(IMAGE와 SPEECH는 묶음이 같다). 모르면 빈 목록. */
  public static java.util.List<String> typesForModel(String loadedModel) {
    return java.util.Arrays.stream(values())
        .filter(t -> t.model.equals(loadedModel))
        .map(Enum::name)
        .toList();
  }
}
