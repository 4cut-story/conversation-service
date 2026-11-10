package net.java21.fourcut.conversation.conversations;

import net.java21.fourcut.conversation.pipeline.Step;

/**
 * 대화 상태. RECORDING → QUEUED → TRANSCRIBING → SELECTING → SCRIPTING → DRAWING → SPEAKING → COMPOSING
 * → DONE. SPEAKING(대사 읽기)은 목소리 기능이 켜진 대화에만 있다. GPU_WAITING은 저장하지 않고 조회할 때 계산한다.
 */
public enum ConversationStatus {
  RECORDING(0),
  QUEUED(5),
  TRANSCRIBING(10),
  SELECTING(35),
  SCRIPTING(55),
  DRAWING(70),
  SPEAKING(85),
  COMPOSING(90),
  DONE(100),
  GPU_WAITING(0),
  FAILED(100),
  NO_SCENE(100);

  private final int progress;

  ConversationStatus(int progress) {
    this.progress = progress;
  }

  /** 대략의 진행률 (0~100). */
  public int progress() {
    return progress;
  }

  public boolean isFinal() {
    return this == DONE || this == FAILED || this == NO_SCENE;
  }

  /** 처리 중(GPU를 기다릴 수 있는) 상태인지. */
  public boolean isProcessing() {
    return this == QUEUED
        || this == TRANSCRIBING
        || this == SELECTING
        || this == SCRIPTING
        || this == DRAWING
        || this == SPEAKING;
  }

  static ConversationStatus of(Step step) {
    return switch (step) {
      case TRANSCRIBE, TRANSCRIBE_PART -> TRANSCRIBING;
      case EXTRACT, SELECT -> SELECTING;
      case SCRIPT, REVIEW -> SCRIPTING;
      case DRAW -> DRAWING;
      case SPEAK -> SPEAKING;
      case COMPOSE -> COMPOSING;
    };
  }
}
