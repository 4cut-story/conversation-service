package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.stt.Transcript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 장면 추출·콘티 전에 전사(STT)의 잘못 들은 말을 외부 LLM API(Claude → Gemini)로 고친다. 뜻을 바꾸거나 없던 말을 더하지 않고, 소리가 비슷해 잘못
 * 적힌 낱말·띄어쓰기·맞춤법만 고친다. 발화의 화자·시각·순서는 그대로 두고 글자만 바꾼다. 호출이 실패하거나 고친 줄이 원문과 너무 다르면 그 부분은 원문을 쓴다.
 */
@Component
class TranscriptCorrector {

  /** 한 번에 보내는 발화 수. 응답에는 고친 줄만 오므로 출력은 짧다. */
  static final int BATCH = 150;

  /** 교정에 쓰는 시간 한도. 서버 단계 시간 초과(5분) 안에 끝나도록 남은 줄은 원문으로 둔다. */
  static final java.time.Duration MAX_TIME = java.time.Duration.ofMinutes(3);

  /** 묶음 앞에 참고로 보여 주는 앞 발화 수 (고치지는 않는다). 묶음 경계에서 맥락이 끊기지 않게 한다. */
  static final int CONTEXT = 5;

  static final String SYSTEM =
      """
      너는 음성 인식(STT, Whisper) 결과를 바로잡는 교정자다. 입력은 가족·친구·동료가 나눈 실제 대화의 전사이고, 교정본은 이 대화를 4컷 만화로 만들 때 \
      장면 고르기와 대사에 그대로 쓰인다. 사람이 녹음을 들었다면 이렇게 적었을 문장으로 고치는 것이 목표다. 내용을 바꾸는 것은 목표가 아니다.

      입력 형식: 줄마다 "[번호] 화자: 내용". 화자는 A, B, C… 가명이다. "참고" 아래 줄은 앞 맥락이니 고치지 않는다.

      고칠 것 (음성 인식 오류만):
      1. 소리가 비슷해 잘못 적힌 낱말. 앞뒤 대화의 맥락으로 원래 말을 알아낼 수 있을 때만 고친다.
         예) "회의 실어" → "회의 싫어", "같이 갈 레" → "같이 갈래?", "오늘 저녁 뭐 머글래" → "오늘 저녁 뭐 먹을래?", \
      "김치 지게" → "김치찌개", "어제 넷풀릭스 봤어" → "어제 넷플릭스 봤어"
      2. 띄어쓰기·맞춤법. 예) "할수있어" → "할 수 있어", "됬어" → "됐어", "어떻게" ↔ "어떡해" 혼동
      3. 문장 부호. 물음이면 "?", 놀람·감탄이면 "!"를 붙인다. 말줄임은 그대로 둔다.
      4. 숫자·단위·외래어 표기. 예) "삼 만 원" → "3만 원", "오후 세시" → "오후 3시", "아이 폰" → "아이폰"
      5. 같은 말이 기계적으로 여러 번 반복된 인식 오류. 예) "그래서 그래서 그래서 그래서 그래서" → "그래서"
      6. 대화와 상관없이 끼어든 인식 환각 줄(영상 끝인사·자막 표기 등). \
      예) "시청해 주셔서 감사합니다", "구독과 좋아요 부탁드려요", "자막 제공: ○○", "MBC 뉴스 ○○○입니다". \
      이런 줄은 drop을 true로 하고 text에는 원문을 그대로 쓴다. 대화 흐름상 실제로 한 말이면 지우지 않는다.

      고치지 말 것:
      - 말투·사투리·반말·존댓말·줄임말·유행어·감탄사("헐", "대박", "ㅋㅋ")는 그대로 둔다. 사투리를 표준어로 바꾸지 않는다.
      - 문장을 더 매끄럽게 다듬거나, 요약하거나, 순서를 바꾸지 않는다. 비문이어도 실제로 그렇게 말했을 법하면 둔다.
      - 없던 낱말·내용·이름을 더하지 않는다. 말이 끊긴 줄을 이어 쓰지 않는다.
      - 사람 이름·별명·지명·가게 이름은 같은 대화의 다른 줄에서 바른 표기가 보일 때만 그 표기로 맞춘다.
      - 확실하지 않으면 고치지 않는다. 고치지 않은 줄 때문에 생기는 손해보다 잘못 고친 줄의 손해가 크다.
      - 화자·줄 번호는 바꾸지 않는다. 줄을 합치거나 나누지 않는다. 대화 언어를 바꾸거나 번역하지 않는다.

      출력: 바뀐 줄만 fixes에 {"i": 줄 번호, "text": 고친 내용} 으로 넣는다. text에는 화자와 번호 없이 내용만 쓴다. \
      바꿀 줄이 없으면 fixes를 빈 배열로 둔다.
      """;

  static final String SCHEMA =
      """
      {"type":"object","properties":{"fixes":{"type":"array","items":{"type":"object",
      "properties":{"i":{"type":"integer"},"text":{"type":"string"},"drop":{"type":"boolean"}},
      "required":["i","text"]}}},"required":["fixes"]}
      """;

  /** 지워도 되는 인식 환각 줄. 모델이 drop을 달아도 이 모양일 때만 지운다 (실제 대화를 지우지 않게). */
  private static final Pattern HALLUCINATION =
      Pattern.compile(
          "시청|구독|좋아요|알림\\s*설정|자막|뉴스|기자입니다|다음\\s*(?:영상|시간)|thanks? for watching|subscribe",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Logger log = LoggerFactory.getLogger(TranscriptCorrector.class);

  private final DirectLlm llm;
  private final JsonMapper json;

  /** {@code llm}은 지연 주입한다. 외부 LLM 실행기가 파이프라인 엔진을 거쳐 이 클래스에 다시 닿는 순환을 끊는다. */
  TranscriptCorrector(@Lazy DirectLlm llm, JsonMapper json) {
    this.llm = llm;
    this.json = json;
  }

  boolean available() {
    return llm.available();
  }

  /** 교정한 전사. 고친 것이 없거나 호출이 모두 실패하면 받은 전사 그대로다. */
  Transcript correct(Transcript transcript) {
    List<Transcript.Utterance> utterances = transcript.utterances();
    Map<Integer, String> fixes = new HashMap<>();
    long deadline = System.nanoTime() + MAX_TIME.toNanos();
    for (int from = 0; from < utterances.size(); from += BATCH) {
      if (System.nanoTime() > deadline) {
        log.warn("전사 교정 시간 한도를 넘겨 {}번 발화부터는 원문으로 둔다", from);
        break;
      }
      int to = Math.min(from + BATCH, utterances.size());
      Optional<String> answer = llm.chat(SYSTEM, prompt(transcript, from, to), SCHEMA);
      if (answer.isPresent()) {
        fixes.putAll(fixes(answer.get(), from, to, utterances));
      } else if (!llm.available()) {
        // 한도 초과 등으로 쉬는 중이면 나머지도 원문으로 둔다
        break;
      }
    }
    if (fixes.isEmpty()) {
      log.info("전사 교정: 발화 {}개, 고친 줄 없음", utterances.size());
      return transcript;
    }
    List<Transcript.Utterance> out = new ArrayList<>(utterances.size());
    int dropped = 0;
    for (int i = 0; i < utterances.size(); i++) {
      Transcript.Utterance u = utterances.get(i);
      String text = fixes.get(i);
      if (text == null) {
        out.add(u);
      } else if (text.isEmpty()) {
        dropped++;
      } else {
        out.add(new Transcript.Utterance(u.speaker(), u.start(), u.end(), text));
      }
    }
    if (out.isEmpty()) {
      return transcript;
    }
    log.info("전사 교정: 발화 {}개 중 {}개 고침, {}개 지움", utterances.size(), fixes.size() - dropped, dropped);
    return new Transcript(transcript.language(), transcript.speakers(), List.copyOf(out));
  }

  static String prompt(Transcript transcript, int from, int to) {
    StringBuilder sb =
        new StringBuilder()
            .append("대화 언어: ")
            .append(transcript.language())
            .append("\n화자: ")
            .append(String.join(", ", transcript.speakers()))
            .append('\n');
    List<Transcript.Utterance> utterances = transcript.utterances();
    int contextFrom = Math.max(0, from - CONTEXT);
    if (contextFrom < from) {
      sb.append("\n참고 (앞 맥락, 고치지 않는다):\n");
      append(sb, utterances, contextFrom, from);
    }
    sb.append("\n고칠 전사 (").append(from).append("~").append(to - 1).append("번):\n");
    append(sb, utterances, from, to);
    return sb.toString();
  }

  private static void append(
      StringBuilder sb, List<Transcript.Utterance> utterances, int from, int to) {
    for (int i = from; i < to; i++) {
      Transcript.Utterance u = utterances.get(i);
      sb.append('[').append(i).append("] ").append(u.speaker()).append(": ").append(u.text());
      sb.append('\n');
    }
  }

  /** 응답에서 받아들일 고친 줄 (번호 → 내용, 지울 줄은 빈 문자열). 번호가 범위 밖이거나, 비었거나, 원문과 너무 다른 줄은 버린다. */
  Map<Integer, String> fixes(String answer, int from, int to, List<Transcript.Utterance> lines) {
    Map<Integer, String> accepted = new HashMap<>();
    JsonNode root;
    try {
      int start = answer.indexOf('{');
      int end = answer.lastIndexOf('}');
      if (start < 0 || end < start) {
        return accepted;
      }
      root = json.readTree(answer.substring(start, end + 1));
    } catch (RuntimeException e) {
      log.warn("전사 교정 응답이 JSON이 아니다: {}", e.getMessage());
      return accepted;
    }
    for (JsonNode node : root.path("fixes")) {
      int i = node.path("i").asInt(-1);
      if (i < from || i >= to) {
        continue;
      }
      String original = lines.get(i).text();
      String text = node.path("text").asString("").strip();
      if (node.path("drop").asBoolean(false)) {
        if (HALLUCINATION.matcher(original).find()) {
          accepted.put(i, "");
        }
      } else if (!text.isEmpty() && !text.equals(original) && similar(original, text)) {
        accepted.put(i, text);
      }
    }
    return accepted;
  }

  /** 고친 줄이 원문을 고친 것으로 볼 만한지. 길이가 크게 바뀌거나 글자가 절반 넘게 바뀌면 지어낸 것으로 보고 버린다 (공백·문장 부호는 세지 않는다). */
  static boolean similar(String original, String corrected) {
    String a = letters(original);
    String b = letters(corrected);
    if (a.isEmpty() || b.isEmpty()) {
      return false;
    }
    if (repeats(a, b)) {
      return true;
    }
    int longer = Math.max(a.length(), b.length());
    boolean lengthOk = Math.abs(a.length() - b.length()) <= Math.max(3, longer / 2);
    return lengthOk && SpokenLines.similarity(a, b) >= 0.5;
  }

  /** 원문이 고친 말을 기계적으로 되풀이한 것인지 ("그래서그래서그래서" → "그래서"). */
  static boolean repeats(String original, String corrected) {
    return corrected.length() >= 2
        && original.length() >= corrected.length() * 3
        && original.replace(corrected, "").length() * 5 <= original.length();
  }

  private static String letters(String text) {
    return text.replaceAll("[\\s\\p{P}\\p{S}]", "");
  }
}
