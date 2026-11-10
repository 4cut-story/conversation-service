package net.java21.fourcut.conversation.comic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.scenario.ComicScript;

/**
 * 대사 읽기 규칙: 콘티 인물 외모에서 성별·나이대를, 컷 표정과 대사에서 감정을 정하고, 읽을 대사에 키를 붙인다. 감정은 GPU 워커가 고르는 감정별 참조
 * 음성(neutral, happy, angry, sad, surprised)이다.
 */
final class SpeechCast {

  static final String NEUTRAL = "neutral";
  static final String HAPPY = "happy";
  static final String ANGRY = "angry";
  static final String SAD = "sad";
  static final String SURPRISED = "surprised";

  private SpeechCast() {}

  /** 읽을 대사 한 줄. key는 "p{컷}-{대사 순번}"이고 순번은 글이 있는 대사만 1부터 센다(자막 순서와 같다). */
  record Spoken(String key, int panel, int line, String speaker, String text, String emotion) {}

  private static final Pattern CHILD =
      Pattern.compile("\\b(girl|boy|child|kid|toddler|schoolgirl|schoolboy)\\b");
  private static final Pattern OLD =
      Pattern.compile(
          "\\b(old|elderly|grandma|grandpa|grandmother|grandfather|granny|senior|gray-haired|"
              + "grey-haired|white-haired|wrinkled)\\b");
  private static final Pattern MIDDLE =
      Pattern.compile(
          "\\b(middle-aged|mother|father|mom|dad|aunt|uncle|forties|fifties|40s|50s|"
              + "housewife|ajumma|ajusshi)\\b");
  private static final Pattern FEMALE =
      Pattern.compile(
          "\\b(woman|women|girl|female|lady|mother|mom|grandma|grandmother|granny|aunt|"
              + "schoolgirl|she|her|housewife|ajumma)\\b");
  private static final Pattern MALE =
      Pattern.compile(
          "\\b(man|men|boy|male|gentleman|father|dad|grandpa|grandfather|uncle|schoolboy|he|"
              + "his|ajusshi)\\b");

  private static final Pattern KO_FEMALE =
      Pattern.compile("(할머니|엄마|어머니|누나|언니|여자|여고생|여학생|딸|손녀|아내|이모|고모|아줌마|아가씨|며느리|공주)");
  private static final Pattern KO_MALE =
      Pattern.compile("(할아버지|아빠|아버지|형|오빠|남자|남고생|남학생|아들|손자|남편|삼촌|아저씨|총각|사위|왕자)");
  private static final Pattern KO_CHILD = Pattern.compile("(아이|어린이|초등|유치원|꼬마|손녀|손자|아기)");
  private static final Pattern KO_OLD =
      Pattern.compile("(할머니|할아버지|할매|할배|노인|어르신|노부부|노모|노부|노신사|원로|증조|은퇴)");
  private static final Pattern KO_MIDDLE =
      Pattern.compile(
          "(엄마|어머니|아빠|아버지|부모|학부모|이모|고모|삼촌|아저씨|아줌마|중년|시어머니|시아버지|장모|장인|"
              + "부장|차장|과장|팀장|국장|사장|회장|대표|원장|교장|교감|교수|이사|임원|"
              + "의원|장관|차관|총리|대통령|시장|도지사|구청장|위원장|청장|판사|검사장)");

  /** 인물 설정의 나이: "40대", "52살", "35세". */
  private static final Pattern KO_AGE = Pattern.compile("(\\d{1,3})\\s*(대|살|세)(?!기)");

  /** 인물 설정의 나이 낱말: 마흔·쉰은 중년, 예순 넘으면 노년. */
  private static final Pattern KO_AGE_WORD = Pattern.compile("(마흔|쉰|예순|일흔|여든|아흔)");

  /** 인물의 성별: 외모(영어)가 먼저이고, 모르면 호칭·인물 설정(한국어)으로 정한다. 모르면 null. */
  static String gender(ComicScript.Character c) {
    String g = gender(c.look());
    if (g != null) {
      return g;
    }
    String ko = korean(c);
    boolean f = KO_FEMALE.matcher(ko).find();
    boolean m = KO_MALE.matcher(ko).find();
    return f == m ? null : f ? "FEMALE" : "MALE";
  }

  /** 인물의 나이대: 외모(영어)가 먼저이고, 모르면 호칭·인물 설정(한국어)으로 정한다. 모르면 null. */
  static String ageGroup(ComicScript.Character c) {
    String fromLook = ageGroup(c.look());
    String ko = korean(c);
    if (fromLook != null && !"YOUNG".equals(fromLook)) {
      return fromLook;
    }
    // 그림용 외모는 어른을 모두 "young woman/man"으로 쓰므로, 나이는 호칭·설정에서 먼저 찾는다
    String byNumber = ageByNumber(ko);
    if (byNumber != null) {
      return byNumber;
    }
    if (KO_OLD.matcher(ko).find()) {
      return "OLD";
    }
    if (KO_CHILD.matcher(ko).find()) {
      return "CHILD";
    }
    if (KO_MIDDLE.matcher(ko).find()) {
      return "MIDDLE";
    }
    return fromLook != null ? fromLook : gender(c) == null ? null : "YOUNG";
  }

  /** 호칭·설정에 적힌 나이(숫자나 마흔·쉰 같은 낱말)로 정한 나이대. 없으면 null. */
  static String ageByNumber(String ko) {
    var m = KO_AGE.matcher(ko);
    if (m.find()) {
      int n = Integer.parseInt(m.group(1));
      if ("대".equals(m.group(2))) {
        // "3대 독자"는 나이가 아니다. 10대는 어린이 목소리보다 젊은 목소리가 맞다
        if (n % 10 != 0) {
          return null;
        }
        n = Math.max(n, 13);
      }
      return n < 13 ? "CHILD" : n < 40 ? "YOUNG" : n < 60 ? "MIDDLE" : "OLD";
    }
    var w = KO_AGE_WORD.matcher(ko);
    if (w.find()) {
      return "마흔".equals(w.group(1)) || "쉰".equals(w.group(1)) ? "MIDDLE" : "OLD";
    }
    return null;
  }

  private static String korean(ComicScript.Character c) {
    return (c.role() == null ? "" : c.role()) + " " + (c.persona() == null ? "" : c.persona());
  }

  /** 외모(영어)에서 성별: FEMALE, MALE, 모르면 null. 먼저 나온 낱말을 따른다. */
  static String gender(String look) {
    String text = lower(look);
    var f = FEMALE.matcher(text);
    var m = MALE.matcher(text);
    boolean hasF = f.find();
    boolean hasM = m.find();
    if (hasF && hasM) {
      return f.start() <= m.start() ? "FEMALE" : "MALE";
    }
    return hasF ? "FEMALE" : hasM ? "MALE" : null;
  }

  /** 외모(영어)에서 나이대: CHILD, OLD, MIDDLE, YOUNG(사람 낱말만 있으면), 모르면 null. */
  static String ageGroup(String look) {
    String text = lower(look);
    if (CHILD.matcher(text).find()) {
      return "CHILD";
    }
    if (OLD.matcher(text).find()) {
      return "OLD";
    }
    if (MIDDLE.matcher(text).find()) {
      return "MIDDLE";
    }
    return gender(look) == null ? null : "YOUNG";
  }

  private static final Pattern FACE_ANGRY =
      Pattern.compile(
          "(angry|anger|furious|fuming|scowl|glar|frown|annoyed|irritat|yell|shout|mad\\b|"
              + "clenched|stomp|grumpy|sulk|pout)");
  private static final Pattern FACE_SAD =
      Pattern.compile(
          "(sad|tear|cry|crying|sob|downcast|gloomy|sigh|teary|depress|lonely|hurt|sorrow|"
              + "droop|dejected|heartbroken)");
  private static final Pattern FACE_SURPRISED =
      Pattern.compile(
          "(surpris|shock|wide-eyed|wide eyes|gasp|startl|stunn|jaw|astonish|amazed|"
              + "flustered|panick|jump)");
  private static final Pattern FACE_HAPPY =
      Pattern.compile(
          "(smil|laugh|grin|happy|joy|cheer|beam|giggl|excited|delight|proud|wink|thumbs up|"
              + "playful)");

  private static final Pattern TEXT_ANGRY =
      Pattern.compile("(화나|화가|짜증|열받|그만해|됐거든|시끄러|야!|왜 이래|너 진짜)");
  private static final Pattern TEXT_SAD = Pattern.compile("(ㅠ|ㅜ|흑|슬퍼|슬프|미안|서운|속상|외로)");
  private static final Pattern TEXT_SURPRISED =
      Pattern.compile("(헉|헐|어머|깜짝|세상에|진짜\\?|정말\\?|뭐\\?|\\?!|!\\?)");
  private static final Pattern TEXT_HAPPY =
      Pattern.compile("(ㅋㅋ|ㅎㅎ|하하|히히|좋아|신나|최고|고마워|감사|축하|행복|맛있)");

  /** 대사의 감정. 그 컷 화자 표정이 먼저이고, 표정으로 모르면 대사 낱말·문장 부호로 정한다. 모르면 neutral. */
  static String emotion(String face, String text) {
    String f = lower(face);
    if (!f.isEmpty()) {
      if (FACE_ANGRY.matcher(f).find()) {
        return ANGRY;
      }
      if (FACE_SAD.matcher(f).find()) {
        return SAD;
      }
      if (FACE_SURPRISED.matcher(f).find()) {
        return SURPRISED;
      }
      if (FACE_HAPPY.matcher(f).find()) {
        return HAPPY;
      }
    }
    String t = text == null ? "" : text;
    if (TEXT_ANGRY.matcher(t).find()) {
      return ANGRY;
    }
    if (TEXT_SURPRISED.matcher(t).find()) {
      return SURPRISED;
    }
    if (TEXT_SAD.matcher(t).find()) {
      return SAD;
    }
    if (TEXT_HAPPY.matcher(t).find()) {
      return HAPPY;
    }
    return NEUTRAL;
  }

  /** 컷의 글이 있는 대사 (자막과 같은 순서). */
  static List<ComicScript.Line> lines(ComicScript.Panel p) {
    return p.lines() == null
        ? List.of()
        : p.lines().stream().filter(l -> l.text() != null && !l.text().isBlank()).toList();
  }

  static String key(int panel, int line) {
    return "p" + panel + "-" + line;
  }

  /** 화자가 있는 대사(해설 제외)에 키와 감정을 붙인다. */
  static List<Spoken> spoken(ComicScript script) {
    List<Spoken> out = new ArrayList<>();
    for (int i = 0; i < script.panels().size(); i++) {
      ComicScript.Panel p = script.panels().get(i);
      List<ComicScript.Line> lines = lines(p);
      for (int k = 0; k < lines.size(); k++) {
        ComicScript.Line l = lines.get(k);
        if (l.speaker() == null || l.speaker().isBlank()) {
          continue;
        }
        out.add(
            new Spoken(
                key(i + 1, k + 1),
                i + 1,
                k + 1,
                l.speaker(),
                l.text().strip(),
                emotion(face(p, l.speaker()), l.text())));
      }
    }
    return out;
  }

  private static String face(ComicScript.Panel p, String speaker) {
    if (p.faces() == null) {
      return null;
    }
    return p.faces().stream()
        .filter(f -> speaker.equals(f.speaker()))
        .map(ComicScript.Face::face)
        .findFirst()
        .orElse(null);
  }

  private static String lower(String s) {
    return s == null ? "" : s.toLowerCase(Locale.ROOT);
  }
}
