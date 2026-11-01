package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.stt.Transcript;

/**
 * LLM에 넣는 전사를 짧게 만든다. 입력 토큰이 줄면 Ollama의 프롬프트 처리 시간이 줄어든다.
 *
 * <ul>
 *   <li>같은 화자가 2초 안에 이어 말하면 한 줄로 합친다 (시각·화자 표시 반복을 없앤다).
 *   <li>홀로 쓰인 군말(어, 음, 으음 등)을 지운다.
 * </ul>
 */
final class TranscriptText {

  static final double MERGE_GAP_SEC = 2.0;

  private static final Pattern FILLER =
      Pattern.compile(
          "(?<=^|\\s)(?:어+|음+|으+음+|에+|uh+|um+|hmm+)[.,…!?~]*(?=\\s|$)",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern SPACES = Pattern.compile("\\s{2,}");

  private TranscriptText() {}

  static String compact(List<Transcript.Utterance> utterances) {
    List<Transcript.Utterance> merged = new ArrayList<>();
    for (Transcript.Utterance u : utterances) {
      String text = clean(u.text());
      if (text.isEmpty()) {
        continue;
      }
      if (!merged.isEmpty()) {
        Transcript.Utterance last = merged.get(merged.size() - 1);
        if (last.speaker().equals(u.speaker()) && u.start() - last.end() <= MERGE_GAP_SEC) {
          merged.set(
              merged.size() - 1,
              new Transcript.Utterance(
                  last.speaker(), last.start(), u.end(), last.text() + " " + text));
          continue;
        }
      }
      merged.add(new Transcript.Utterance(u.speaker(), u.start(), u.end(), text));
    }
    return Transcript.lines(merged);
  }

  static String clean(String text) {
    if (text == null) {
      return "";
    }
    return SPACES.matcher(FILLER.matcher(text).replaceAll("")).replaceAll(" ").strip();
  }
}
