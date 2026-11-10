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

  /** 교정 전 전사 원본. 전사를 LLM으로 교정하면 원본을 여기에 남긴다 (다시 시도해도 원본에서 교정한다). */
  public static String rawTranscript(String conversationId) {
    return work(conversationId, "transcript-raw.json");
  }

  /** 녹음 중 미리 전사한 앞부분 (FR-015). 화자 없이 단어 시각만 있다. */
  public static String preTranscript(String conversationId) {
    return work(conversationId, "pre-transcript.json");
  }

  /** 기사로 만들기에서 골라낸 기사 본문 (제목, 출처, 본문). */
  public static String article(String conversationId) {
    return work(conversationId, "article.json");
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

  /** 라이브 모드 움직임 컷의 두 번째 그림(말풍선 없음, 워커가 올린다). */
  public static String panelB(String conversationId, int index) {
    return work(conversationId, "panel-" + index + "-b.png");
  }

  /** 라이브 모드 움직임 컷의 세 번째 그림(두 번째와 다르게 바꾼 그림, 워커가 올린다). */
  public static String panelC(String conversationId, int index) {
    return work(conversationId, "panel-" + index + "-c.png");
  }

  /** 라이브 모드 움직임 컷의 네 번째 그림(넓은 그림 컷만, 워커가 올린다, FR-049). */
  public static String panelD(String conversationId, int index) {
    return work(conversationId, "panel-" + index + "-d.png");
  }

  /** 움직임 그림. frame 1은 두 번째(-b), 2는 세 번째(-c), 3은 네 번째(-d). */
  public static String panelFrame(String conversationId, int index, int frame) {
    return switch (frame) {
      case 2 -> panelC(conversationId, index);
      case 3 -> panelD(conversationId, index);
      default -> panelB(conversationId, index);
    };
  }

  /** 라이브 모드 전용 넓은 그림(16:9, 말풍선·글자 없음, 워커가 완성 컷을 바탕으로 넓혀 그린다). */
  public static String panelWide(String conversationId, int index) {
    return work(conversationId, "panel-" + index + "-w.png");
  }

  /** 라이브 넓은 그림의 깊이 지도(같은 크기 흑백, 밝을수록 가깝다, FR-048). */
  public static String panelDepth(String conversationId, int index) {
    return work(conversationId, "panel-" + index + "-depth.png");
  }

  /** 라이브 깊이 지도의 만화 폴더 기준 상대 경로. */
  public static String liveDepthPath(int index) {
    return "panels/" + index + "-depth.png";
  }

  /** 라이브 깊이 지도. */
  public static String liveDepth(String conversationId, int index) {
    return comicPrefix(conversationId) + liveDepthPath(index);
  }

  /** 만화 폴더 (원본·썸네일·컷 그림·재생 정보가 함께 있다). */
  public static String comicPrefix(String conversationId) {
    return "comics/" + conversationId + "/";
  }

  /** 라이브 모드 컷 그림의 만화 폴더 기준 상대 경로. second면 두 번째 그림. */
  public static String livePanelPath(int index, boolean second) {
    return livePanelPath(index, second ? 1 : 0);
  }

  /** 라이브 모드 컷 그림의 만화 폴더 기준 상대 경로. frame 0은 첫 그림, 1은 두 번째(-b), 2는 세 번째(-c), 3은 네 번째(-d). */
  public static String livePanelPath(int index, int frame) {
    return "panels/" + index + (frame <= 0 ? "" : "-" + (char) ('a' + frame)) + ".png";
  }

  /** 라이브 모드 컷 그림(말풍선 포함). */
  public static String livePanel(String conversationId, int index, boolean second) {
    return comicPrefix(conversationId) + livePanelPath(index, second);
  }

  /** 라이브 모드 컷 그림. frame은 {@link #livePanelPath(int, int)}와 같다. */
  public static String livePanel(String conversationId, int index, int frame) {
    return comicPrefix(conversationId) + livePanelPath(index, frame);
  }

  /** 대사 읽기 결과(대사 키 → 음성 길이). */
  public static String speech(String conversationId) {
    return work(conversationId, "speech.json");
  }

  /** 워커가 올리는 대사 음성. key는 "p{컷}-{대사 순번}"이다. */
  public static String speechAudio(String conversationId, String key) {
    return work(conversationId, "live/voice/" + key + ".mp3");
  }

  /** 라이브 모드 대사 음성 경로 (만화 폴더 기준, common live-mode.yaml CaptionVoice). */
  public static String liveVoicePath(int panel, int line) {
    return "voice/" + panel + "-" + line + ".mp3";
  }

  public static String liveVoice(String conversationId, int panel, int line) {
    return comicPrefix(conversationId) + liveVoicePath(panel, line);
  }

  /** 라이브 모드 재생 정보. */
  public static String liveJson(String conversationId) {
    return comicPrefix(conversationId) + "live.json";
  }

  public static String comicFull(String conversationId) {
    return "comics/" + conversationId + "/full.png";
  }

  public static String comicThumb(String conversationId) {
    return "comics/" + conversationId + "/thumb.png";
  }

  /** 캐릭터 DB에 없는 등장인물의 얼굴 그림 (만화 컷에서 잘라 낸 것). index는 콘티 인물 순서(0부터). */
  public static String castPortrait(String conversationId, int index) {
    return "comics/" + conversationId + "/cast-" + index + ".png";
  }
}
