package net.java21.fourcut.conversation.scenario;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 4컷 콘티 (docs specs/001 pipeline-schemas.md). 규칙: 컷 4개(기·승·전·결), 컷당 대사 2문장 이내, 대화와 같은 언어, 실명 없음. 네
 * 컷은 서로 다른 순간이어야 해서 구도(shot)가 모두 같거나 장면 설명이 거의 같으면 위반이다.
 *
 * @param nameWarnings 콘티를 만들며 가명으로 바꾼 실명 의심 단어. 올리기 전 경고에 쓴다
 */
public record ComicScript(
    String title,
    List<Character> characters,
    List<Panel> panels,
    Boolean reviewed,
    List<String> nameWarnings) {

  /**
   * 등장인물.
   *
   * @param speaker 전사의 화자 표시(A, B…). 대사와 장면이 이 값으로 인물을 가리킨다
   * @param name 말풍선에 보이는 가명 (예: 민지). 예전 콘티에는 없을 수 있다
   * @param persona 한 줄 인물 설정
   * @param look 그림용 영어 외모
   */
  public record Character(String speaker, String name, String persona, String look) {

    /** 말풍선에 쓰는 이름. 가명이 없으면 화자 표시. */
    public String displayName() {
      return name == null || name.isBlank() ? speaker : name.strip();
    }
  }

  /**
   * 컷 하나.
   *
   * @param role 기·승·전·결
   * @param shot 구도: wide, medium, close-up. 예전 콘티에는 없을 수 있다
   * @param scene 그림 모델용 영어 장면. 인물은 {A}, {B}로 쓴다
   */
  public record Panel(String role, String shot, String scene, List<Line> lines) {}

  /** 말풍선 하나. speaker는 화자 표시(A, B…). */
  public record Line(String speaker, String text) {}

  /** 컷당 최대 말풍선 수. */
  public static final int MAX_LINES = 2;

  /** 두 컷의 장면 단어가 이만큼 겹치면 같은 그림으로 본다. */
  static final double MAX_SCENE_OVERLAP = 0.7;

  static final Set<String> SHOTS = Set.of("wide", "medium", "close-up");

  private static final List<String> ROLES = List.of("기", "승", "전", "결");

  /** 화자 표시 → 말풍선 이름. */
  public Map<String, String> displayNames() {
    return characters.stream()
        .collect(
            Collectors.toMap(
                Character::speaker, Character::displayName, (a, b) -> a, java.util.HashMap::new));
  }

  /** 화자 표시 → 인물. */
  public Map<String, Character> bySpeaker() {
    return characters.stream()
        .collect(Collectors.toMap(Character::speaker, Function.identity(), (a, b) -> a));
  }

  /** 규칙 위반 내용. 없으면 null. */
  public String violation() {
    if (title == null || title.isBlank() || title.length() > 100) {
      return "제목이 없거나 100자를 넘는다";
    }
    if (characters == null || characters.isEmpty() || panels == null || panels.size() != 4) {
      return "등장인물이 없거나 컷이 4개가 아니다";
    }
    String cast = castViolation();
    if (cast != null) {
      return cast;
    }
    Set<String> speakers = Set.copyOf(characters.stream().map(Character::speaker).toList());
    for (int i = 0; i < 4; i++) {
      Panel p = panels.get(i);
      if (!ROLES.get(i).equals(p.role()) || p.scene() == null || p.scene().isBlank()) {
        return (i + 1) + "번 컷의 역할이나 장면 설명이 틀렸다";
      }
      if (!SHOTS.contains(p.shot())) {
        return (i + 1) + "번 컷의 구도(shot)가 틀렸다";
      }
      List<Line> lines = p.lines() == null ? List.of() : p.lines();
      if (lines.size() > MAX_LINES) {
        return (i + 1) + "번 컷 대사가 2개를 넘는다";
      }
      for (Line l : lines) {
        if (l.text() == null || l.text().isBlank() || !speakers.contains(l.speaker())) {
          return (i + 1) + "번 컷 대사의 화자나 내용이 틀렸다";
        }
      }
    }
    return repetition();
  }

  private String castViolation() {
    Set<String> names = new HashSet<>();
    for (Character c : characters) {
      String name = c.name() == null ? "" : c.name().strip();
      if (name.isEmpty() || name.length() > 10 || name.equals(c.speaker())) {
        return c.speaker() + "의 가명이 없거나 틀렸다";
      }
      if (!names.add(name)) {
        return "가명 " + name + "이(가) 겹친다";
      }
      if (c.look() == null || c.look().isBlank()) {
        return c.speaker() + "의 외모 설명이 없다";
      }
    }
    return null;
  }

  /** 네 컷이 서로 다른 순간인지: 구도가 모두 같거나 장면 단어가 거의 같은 컷이 있으면 위반. */
  private String repetition() {
    if (panels.stream().map(Panel::shot).distinct().count() == 1) {
      return "네 컷의 구도가 모두 같다";
    }
    for (int i = 0; i < 4; i++) {
      for (int j = i + 1; j < 4; j++) {
        if (overlap(panels.get(i).scene(), panels.get(j).scene()) >= MAX_SCENE_OVERLAP) {
          return (i + 1) + "번과 " + (j + 1) + "번 컷 장면이 거의 같다";
        }
      }
    }
    return null;
  }

  /** 두 장면의 단어 겹침(Jaccard). */
  static double overlap(String a, String b) {
    Set<String> x = words(a);
    Set<String> y = words(b);
    if (x.isEmpty() || y.isEmpty()) {
      return 0;
    }
    Set<String> both = new HashSet<>(x);
    both.retainAll(y);
    Set<String> any = new HashSet<>(x);
    any.addAll(y);
    return (double) both.size() / any.size();
  }

  private static Set<String> words(String s) {
    return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^a-z0-9{}]+"))
        .filter(w -> !w.isBlank())
        .collect(Collectors.toSet());
  }

  /** 대화 원문에 나온 가명. 실명과 겹칠 수 있어 콘티를 다시 만든다. */
  public List<String> namesFoundIn(String transcriptText) {
    return characters.stream()
        .map(Character::displayName)
        .filter(n -> !n.isBlank() && transcriptText.contains(n))
        .toList();
  }

  ComicScript reviewedWith(List<String> warnings) {
    return new ComicScript(
        title, characters, panels, true, warnings == null ? List.of() : warnings);
  }
}
