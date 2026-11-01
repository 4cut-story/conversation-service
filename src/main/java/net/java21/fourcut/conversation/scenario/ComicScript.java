package net.java21.fourcut.conversation.scenario;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * 4컷 콘티 (docs specs/001 pipeline-schemas.md). 규칙: 컷 4개(기·승·전·결), 컷당 대사 2문장 이내, 대화와 같은 언어, 실명 없음. 네
 * 컷은 서로 다른 순간이어야 해서 구도(shot)가 모두 같거나 장면 설명이 거의 같으면 위반이다.
 *
 * @param nameWarnings 콘티를 만들며 가명으로 바꾼 실명 의심 단어. 올리기 전 경고에 쓴다
 * @param concept 그림 컨셉({@link Concept} 이름). 예전 콘티에는 없을 수 있다(일상 웹툰)
 */
public record ComicScript(
    String title,
    List<Character> characters,
    List<Panel> panels,
    Boolean reviewed,
    List<String> nameWarnings,
    String concept) {

  public ComicScript(
      String title,
      List<Character> characters,
      List<Panel> panels,
      Boolean reviewed,
      List<String> nameWarnings) {
    this(title, characters, panels, reviewed, nameWarnings, null);
  }

  /** 컨셉을 정한 콘티. */
  public ComicScript withConcept(Concept c) {
    return new ComicScript(title, characters, panels, reviewed, nameWarnings, c.name());
  }

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
  static final double MAX_SCENE_OVERLAP = 0.8;

  /** 장면 겹침을 잴 때 빼는 흔한 단어. */
  private static final Set<String> STOPWORDS =
      Set.of("a", "an", "the", "and", "at", "in", "on", "of", "with", "to", "is", "are", "nearby");

  static final Set<String> SHOTS = Set.of("wide", "medium", "close-up");

  private static final List<String> ROLES = List.of("기", "승", "전", "결");

  /** 화자 표시 → 말풍선 이름. */
  public Map<String, String> displayNames() {
    return characters.stream()
        .collect(
            Collectors.toMap(
                Character::speaker, Character::displayName, (a, b) -> a, HashMap::new));
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

  /** 네 컷이 서로 다른 순간인지: 구도가 모두 같거나, 같은 대사가 두 번 나오거나, 장면 단어가 거의 같은 컷이 있으면 위반. */
  private String repetition() {
    if (panels.stream().map(Panel::shot).distinct().count() == 1) {
      return "네 컷의 구도가 모두 같다";
    }
    Set<String> said = new HashSet<>();
    for (Panel p : panels) {
      for (Line l : p.lines() == null ? List.<Line>of() : p.lines()) {
        if (!said.add(l.text().replaceAll("\\s+", ""))) {
          return "같은 대사가 두 번 나온다: " + l.text();
        }
      }
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
    return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
        .filter(w -> w.length() > 1 && !STOPWORDS.contains(w))
        .collect(Collectors.toSet());
  }

  /** 대화 원문에 나온 가명. 실명일 수 있다. */
  public List<String> namesFoundIn(String transcriptText) {
    return characters.stream()
        .map(Character::displayName)
        .filter(n -> !n.isBlank() && transcriptText.contains(n))
        .toList();
  }

  /** 대화에 나온 이름과 겹칠 때 대신 쓰는 가명. */
  static final List<String> SPARE_NAMES = CastNames.POOL;

  /** 코드로 정한 가명을 인물에 넣는다(LLM이 다른 이름을 냈어도 덮어쓴다). 대사·제목 속 LLM 가명도 함께 바꾼다. */
  public ComicScript withNames(Map<String, String> assigned) {
    if (assigned.isEmpty() || characters == null) {
      return this;
    }
    Map<String, String> rename = new HashMap<>();
    List<Character> cast =
        characters.stream()
            .map(
                c -> {
                  String name = assigned.get(c.speaker());
                  if (name == null) {
                    return c;
                  }
                  if (c.name() != null && !c.name().isBlank() && !c.name().strip().equals(name)) {
                    rename.put(c.name().strip(), name);
                  }
                  return new Character(c.speaker(), name, c.persona(), c.look());
                })
            .toList();
    List<Panel> fixed = mapLines(l -> new Line(l.speaker(), replaceAll(l.text(), rename)));
    return new ComicScript(replaceAll(title, rename), cast, fixed, reviewed, nameWarnings, concept);
  }

  /**
   * 가명이 대화 원문에 나오면(LLM이 대화 속 실명을 가명으로 쓴 경우) 원문에 없는 예비 가명으로 바꾼다. 대사와 제목에 그 이름이 있으면 함께 바꾼다. 다시 만들지 않고
   * 코드로 고친다(qwen3는 같은 실명을 반복해서 고른다).
   */
  public ComicScript withFictionalNames(String transcriptText) {
    List<String> clashes = namesFoundIn(transcriptText);
    if (clashes.isEmpty()) {
      return this;
    }
    Set<String> used = new HashSet<>(characters.stream().map(Character::displayName).toList());
    Map<String, String> rename = new HashMap<>();
    Iterator<String> spare =
        SPARE_NAMES.stream()
            .filter(n -> !transcriptText.contains(n) && !used.contains(n))
            .iterator();
    for (String old : clashes) {
      if (spare.hasNext()) {
        rename.put(old, spare.next());
      }
    }
    List<Character> cast =
        characters.stream()
            .map(
                c ->
                    rename.containsKey(c.displayName())
                        ? new Character(
                            c.speaker(), rename.get(c.displayName()), c.persona(), c.look())
                        : c)
            .toList();
    List<Panel> fixed = mapLines(l -> new Line(l.speaker(), replaceAll(l.text(), rename)));
    return new ComicScript(replaceAll(title, rename), cast, fixed, reviewed, nameWarnings, concept);
  }

  /**
   * LLM이 nameWarnings에 적고도 대사·제목에 남긴 실명을 "친구"로 바꾼다("지현이가" → "친구가"). 대사에 섞여 나온 JSON 조각({ } ")도 지운다.
   */
  public ComicScript scrubbed(Predicate<String> isName) {
    Map<String, String> rename = new HashMap<>();
    for (String w : nameWarnings == null ? List.<String>of() : nameWarnings) {
      String word = w == null ? "" : w.strip();
      String stem =
          word.length() > 2 && word.endsWith("이") ? word.substring(0, word.length() - 1) : word;
      if (stem.length() < 2 || !(isName.test(word) || isName.test(stem))) {
        continue;
      }
      rename.put(word, RealNames.UNKNOWN);
      rename.put(stem, RealNames.UNKNOWN);
    }
    ComicScript out = replaced(rename);
    List<Panel> fixed = out.mapLines(l -> new Line(l.speaker(), stripJson(l.text())));
    return new ComicScript(out.title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 모든 대사 문장을 f로 바꾼다. */
  public ComicScript withLineTexts(UnaryOperator<String> f) {
    return new ComicScript(
        title,
        characters,
        mapLines(l -> new Line(l.speaker(), f.apply(l.text()))),
        reviewed,
        nameWarnings,
        concept);
  }

  /** keep을 통과한 대사만 남긴다(장면 밖 대화에서 가져온 대사를 버린다). */
  public ComicScript withLinesWhere(Predicate<String> keep) {
    List<Panel> fixed =
        panels == null
            ? List.of()
            : panels.stream()
                .map(
                    p ->
                        new Panel(
                            p.role(),
                            p.shot(),
                            p.scene(),
                            p.lines() == null
                                ? null
                                : p.lines().stream().filter(l -> keep.test(l.text())).toList()))
                .toList();
    return new ComicScript(title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 인물 외모의 첫 단어(동물)를 코드로 정한 동물로 바꾼다. LLM은 두 인물을 같은 동물로 자주 고른다. */
  public ComicScript withAnimals(List<String> animals) {
    if (characters == null || animals.isEmpty()) {
      return this;
    }
    List<Character> cast = new java.util.ArrayList<>();
    for (int i = 0; i < characters.size(); i++) {
      Character c = characters.get(i);
      String look = c.look() == null ? "" : c.look().strip();
      int comma = look.indexOf(',');
      String rest = comma < 0 ? "" : look.substring(comma);
      cast.add(
          new Character(
              c.speaker(), c.name(), c.persona(), animals.get(i % animals.size()) + rest));
    }
    return new ComicScript(title, cast, panels, reviewed, nameWarnings, concept);
  }

  /** 대사와 제목의 단어를 바꾼다(긴 단어부터). */
  public ComicScript replaced(Map<String, String> rename) {
    if (rename.isEmpty()) {
      return this;
    }
    List<Panel> fixed = mapLines(l -> new Line(l.speaker(), replaceAll(l.text(), rename)));
    return new ComicScript(
        replaceAll(title, rename), characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 모든 컷의 대사를 f로 바꾼 새 컷 목록. 컷이 없으면 빈 목록(검증에서 컷 수 위반이 된다). */
  private List<Panel> mapLines(UnaryOperator<Line> f) {
    if (panels == null) {
      return List.of();
    }
    return panels.stream()
        .map(
            p ->
                new Panel(
                    p.role(),
                    p.shot(),
                    p.scene(),
                    p.lines() == null ? null : p.lines().stream().map(f).toList()))
        .toList();
  }

  private static String stripJson(String text) {
    return text == null ? null : text.replaceAll("[{}\"]", "").replaceAll(",\\s*$", "").strip();
  }

  private static String replaceAll(String text, Map<String, String> rename) {
    return RealNames.apply(text, rename);
  }

  /** LLM이 자주 내는 작은 형식 차이를 맞춘다: 대사 화자를 가명("민지")으로 쓴 경우 화자 표시(A)로 바꾼다. 검증 전에 부른다. */
  public ComicScript normalized() {
    if (characters == null || panels == null) {
      return this;
    }
    Map<String, String> byName = new HashMap<>();
    for (Character c : characters) {
      if (c.name() != null && !c.name().isBlank()) {
        byName.put(c.name().strip(), c.speaker());
      }
    }
    List<Panel> fixed =
        mapLines(
            l ->
                new Line(
                    l.speaker() == null
                        ? null
                        : byName.getOrDefault(l.speaker().strip(), l.speaker().strip()),
                    l.text()));
    return new ComicScript(
        title,
        characters,
        fixed.stream().map(ComicScript::withSpeakersInScene).toList(),
        reviewed,
        nameWarnings,
        concept);
  }

  /** 대사가 있는 인물이 장면 설명에 없으면 그림에도 나오도록 붙인다("{B} looks surprised" + ", {A} nearby"). */
  static Panel withSpeakersInScene(Panel p) {
    if (p.scene() == null || p.lines() == null) {
      return p;
    }
    StringBuilder scene = new StringBuilder(p.scene().strip());
    p.lines().stream()
        .map(Line::speaker)
        .filter(sp -> sp != null && !sp.isBlank())
        .distinct()
        .filter(sp -> !scene.toString().contains("{" + sp + "}"))
        .forEach(sp -> scene.append(", {").append(sp).append("} nearby"));
    return new Panel(p.role(), p.shot(), scene.toString(), p.lines());
  }

  ComicScript reviewedWith(List<String> warnings) {
    return new ComicScript(
        title, characters, panels, true, warnings == null ? List.of() : warnings, concept);
  }
}
