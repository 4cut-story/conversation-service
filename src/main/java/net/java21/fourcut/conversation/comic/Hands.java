package net.java21.fourcut.conversation.comic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.scenario.ComicScript;

/**
 * 한 인물에게 손이 둘보다 많이 그려지지 않게 콘티의 손 동작을 하나로 줄인다. 장면(action)이 이미 손을 쓰는데(타자 치기, 물건 들기) 표정 칸(faces)이나 장면
 * 뒷부분에 턱 괴기·허리에 손 같은 다른 손 자세가 함께 오면, 그림 모델이 두 동작을 모두 그려 손이 셋이 된다(키보드 위 두 손 + 턱 괸 손). 장면의 주 동작을 남기고
 * 덧붙은 손 자세를 뺀다.
 */
final class Hands {

  private Hands() {}

  /** 손·팔을 쓰는 동작. 이것이 있으면 장면이 이미 손을 쓴다고 본다. */
  static final Pattern USES =
      Pattern.compile(
          "(?i)\\b(hold(s|ing)?|grip(s|ping)?|typ(e|es|ing)|press(es|ing)?|carr(y|ies|ying)|hug(s|ging)?"
              + "|rais(e|es|ing)|lift(s|ing)?|point(s|ing)?|reach(es|ing)?|clutch(es|ing)?|wav(e|es|ing)"
              + "|writ(e|es|ing)|tap(s|ping)?|scroll(s|ing)?|clap(s|ping)?|push(es|ing)?|pull(s|ing)?"
              + "|drink(s|ing)?|eat(s|ing)?|stir(s|ring)?|cut(s|ting)?|wip(e|es|ing)|pour(s|ing)?"
              + "|throw(s|ing)?|catch(es|ing)?|swing(s|ing)?|feed(s|ing)?|pat(s|ting)?|squeez(e|es|ing)"
              + "|offer(s|ing)?|giv(e|es|ing)|tak(e|es|ing)|pick(s|ing)?|grab(s|bing)?|shak(e|es|ing)"
              + "|hands?|fists?|arms?|fingers?|palms?|keyboard|mouse|phone|smartphone|controller)\\b");

  /** 표정 칸에 들어온 손·팔 자세("hand on chin", "hands on cheeks", "arms crossed"). */
  static final Pattern GESTURE =
      Pattern.compile(
          "(?i)\\b(hands?|fists?|arms?|fingers?|palms?|thumbs?(-up)?|elbows?|clapping|waving|pointing"
              + "|saluting|shrug(s|ging)?|facepalm(s|ing)?|chin[- ]resting|chin in|chin on)\\b");

  /** 장면 뒷부분에 붙은 쉬는 손 자세: 턱·볼·이마·머리를 손에 괴거나 허리에 손을 얹는 것. */
  static final Pattern REST =
      Pattern.compile(
          "(?i)(\\b(chin|cheek|cheeks|forehead|temple|head|jaw)\\b.*\\b(hand|hands|fist|fists|palm|palms|fingers?|knuckles?)\\b"
              + "|\\b(hand|hands|fist|fists|palm|palms|fingers?)\\b.*\\b(on|to|under|against|at)\\b.*"
              + "\\b(chin|cheek|cheeks|forehead|temple|head|jaw|hips?|waist|mouth)\\b"
              + "|\\b(scratch|rub|strok)\\w*\\b.*\\b(head|chin)\\b|\\bthinking pose\\b)");

  /** 장면을 나누는 곳: 쉼표, while, and. */
  private static final Pattern CLAUSE = Pattern.compile("\\s*,\\s*|\\s+while\\s+|\\s+and\\s+");

  /** 문장 앞 인물 자리표("{A} "). */
  private static final Pattern SUBJECT = Pattern.compile("^\\s*(\\{[A-Z]})\\s+");

  /** 모든 컷의 손 동작을 하나로 줄인 콘티. 바뀐 것이 없으면 그대로 돌려준다. */
  static ComicScript spare(ComicScript script) {
    if (script.panels() == null) {
      return script;
    }
    List<ComicScript.Panel> out = new ArrayList<>();
    boolean changed = false;
    for (ComicScript.Panel p : script.panels()) {
      ComicScript.Panel next = spare(p);
      changed |= !java.util.Objects.equals(next, p);
      out.add(next);
    }
    return changed
        ? new ComicScript(
            script.title(),
            script.characters(),
            out,
            script.reviewed(),
            script.nameWarnings(),
            script.concept(),
            script.summary(),
            script.mood())
        : script;
  }

  /** 컷 하나의 손 동작을 하나로 줄인다. */
  static ComicScript.Panel spare(ComicScript.Panel p) {
    if (p == null || p.scene() == null) {
      return p;
    }
    String scene = action(p.scene());
    List<ComicScript.Face> faces = p.faces();
    if (faces != null && USES.matcher(scene).find()) {
      List<ComicScript.Face> kept = new ArrayList<>();
      for (ComicScript.Face f : faces) {
        kept.add(f == null ? null : new ComicScript.Face(f.speaker(), face(f.face())));
      }
      faces = kept.equals(faces) ? faces : kept;
    }
    if (scene.equals(p.scene()) && java.util.Objects.equals(faces, p.faces())) {
      return p;
    }
    return new ComicScript.Panel(
        p.role(),
        p.shot(),
        scene,
        p.lines(),
        p.setting(),
        faces,
        p.sound(),
        p.motion(),
        p.effect(),
        p.ambient(),
        p.stir());
  }

  /**
   * 장면에 손을 쓰는 동작과 쉬는 손 자세가 함께 있으면 쉬는 자세를 뺀다. 쉬는 자세가 맨 앞(주 동작)이면 그것을 남기지 않고 뒤의 손 쓰는 동작을 앞 인물에 붙인다
   * ("{A} rests chin on a fist, typing on {노트북}" → "{A} typing on {노트북}"): 손 쓰는 동작에는 이야기의 물건이 걸려
   * 있다.
   */
  static String action(String scene) {
    List<String> parts = new ArrayList<>();
    List<String> seps = new ArrayList<>();
    Matcher m = CLAUSE.matcher(scene);
    int at = 0;
    while (m.find()) {
      parts.add(scene.substring(at, m.start()));
      seps.add(m.group());
      at = m.end();
    }
    parts.add(scene.substring(at));
    if (parts.size() < 2) {
      return scene;
    }
    boolean[] rest = new boolean[parts.size()];
    boolean uses = false;
    boolean any = false;
    for (int i = 0; i < parts.size(); i++) {
      rest[i] = REST.matcher(parts.get(i)).find();
      any |= rest[i];
      uses |= !rest[i] && USES.matcher(parts.get(i)).find();
    }
    if (!any || !uses) {
      return scene;
    }
    String subject = "";
    if (rest[0]) {
      Matcher s = SUBJECT.matcher(parts.get(0));
      if (!s.find()) {
        return scene;
      }
      subject = s.group(1) + " ";
    }
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < parts.size(); i++) {
      if (rest[i]) {
        continue;
      }
      if (out.length() > 0) {
        out.append(seps.get(i - 1));
      } else {
        out.append(subject);
      }
      out.append(parts.get(i).strip());
    }
    return out.toString();
  }

  /** 표정 칸에서 손·팔 자세 마디를 뺀다. 다 빠지면 null. */
  static String face(String face) {
    if (face == null) {
      return null;
    }
    List<String> kept = new ArrayList<>();
    for (String part : face.split(",")) {
      if (!part.isBlank() && !GESTURE.matcher(part).find()) {
        kept.add(part.strip());
      }
    }
    if (kept.isEmpty()) {
      return null;
    }
    String out = String.join(", ", kept);
    return out.equals(face.strip()) ? face : out;
  }
}
