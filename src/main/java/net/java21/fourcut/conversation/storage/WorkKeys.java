package net.java21.fourcut.conversation.storage;

import java.util.Locale;

/** 객체 키 규칙. 대화마다 접두어 하나로 모아 지울 때 한 번에 지운다. */
public final class WorkKeys {

  private WorkKeys() {}

  /** 원본 음성 조각. 전사가 끝나면 지운다 (FR-009). */
  public static String audioPrefix(String conversationId) {
    return "audio-raw/" + conversationId + "/";
  }

  public static String audioChunk(String conversationId, int index) {
    return audioPrefix(conversationId) + String.format(Locale.ROOT, "%02d", index);
  }

  /** 단계 결과 (7일 보관). */
  public static String workPrefix(String conversationId) {
    return "pipeline-work/" + conversationId + "/";
  }

  public static String work(String conversationId, String name) {
    return workPrefix(conversationId) + name;
  }

  public static String transcript(String conversationId) {
    return work(conversationId, "transcript.json");
  }

  public static String candidates(String conversationId) {
    return work(conversationId, "candidates.json");
  }

  public static String script(String conversationId) {
    return work(conversationId, "script.json");
  }

  public static String panel(String conversationId, int index) {
    return work(conversationId, "panel-" + index + ".png");
  }

  public static String comicFull(String conversationId) {
    return "comics/" + conversationId + "/full.png";
  }

  public static String comicThumb(String conversationId) {
    return "comics/" + conversationId + "/thumb.png";
  }
}
