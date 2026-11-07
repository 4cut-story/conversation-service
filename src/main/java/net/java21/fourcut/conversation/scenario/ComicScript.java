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
 * 콘티 (docs specs/001 pipeline-schemas.md). 규칙: 컷 4~10개(기·승·전·결 흐름), 컷당 대사 2문장 이내, 대화와 같은 언어, 실명 없음.
 * 컷 수는 콘티를 만드는 LLM이 이야기에 맞게 고른다(기본 4컷). 모든 컷은 서로 다른 순간이어야 해서 구도(shot)가 모두 같거나 장면 설명이 거의 같으면 위반이다.
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
   * @param role 대사와 말풍선에서 이 인물을 부르는 호칭(할머니, 손녀, 친구…). 예전 콘티에는 없다
   * @param lookId 그림 단계에서 찾은 캐릭터 모습 공개 ID. 못 찾았거나 그리기 전이면 null
   */
  public record Character(
      String speaker, String name, String persona, String look, String role, String lookId) {

    public Character(String speaker, String name, String persona, String look) {
      this(speaker, name, persona, look, null, null);
    }

    /** 등장인물 이름(가명, 그림 단계 뒤에는 캐릭터 이름). 이름이 없으면 화자 표시. */
    public String displayName() {
      return name == null || name.isBlank() ? speaker : name.strip();
    }

    /** 말풍선 앞과 대사에서 부르는 말: 호칭, 없으면(예전 콘티) 이름. */
    public String callName() {
      return role == null || role.isBlank() ? displayName() : role.strip();
    }

    public Character withName(String newName) {
      return new Character(speaker, newName, persona, look, role, lookId);
    }

    public Character withLook(String newLook) {
      return new Character(speaker, name, persona, newLook, role, lookId);
    }

    public Character withRole(String newRole) {
      return new Character(speaker, name, persona, look, newRole, lookId);
    }

    public Character withLookId(String newLookId) {
      return new Character(speaker, name, persona, look, role, newLookId);
    }
  }

  /**
   * 컷 하나.
   *
   * @param role 기·승·전·결
   * @param shot 구도: wide, medium, close-up. 예전 콘티에는 없을 수 있다
   * @param scene 그림 모델용 영어 장면. 인물은 {A}, {B}로 쓴다
   * @param setting 그림 모델용 영어 배경: 장소와 눈에 보이는 배경 물건. 예전 콘티에는 없다
   * @param faces 인물별 표정·몸짓. 예전 콘티에는 없다
   */
  public record Panel(
      String role, String shot, String scene, List<Line> lines, String setting, List<Face> faces) {

    public Panel(String role, String shot, String scene, List<Line> lines) {
      this(role, shot, scene, lines, null, null);
    }

    /** 대사만 바꾼 컷. */
    public Panel withLines(List<Line> next) {
      return new Panel(role, shot, scene, next, setting, faces);
    }

    /** 장면만 바꾼 컷. */
    public Panel withScene(String next) {
      return new Panel(role, shot, next, lines, setting, faces);
    }
  }

  /**
   * 컷 속 인물 한 명의 표정·몸짓.
   *
   * @param speaker 화자 표시(A, B…)
   * @param face 그림 모델용 영어 3단어 이내(예: wide-eyed, hands up)
   */
  public record Face(String speaker, String face) {}

  /**
   * 말풍선 하나.
   *
   * @param speaker 화자 표시(A, B…)
   * @param text 말풍선에 보이는 대사. 컨셉 말투로 다듬은 말
   * @param quote 대화 원문에서 고른 말(다듬기 전). 원문 대조·장면 확인·빈 컷 채우기의 기준이 된다. 예전 콘티에는 없다
   */
  public record Line(String speaker, String text, String quote) {

    public Line(String speaker, String text) {
      this(speaker, text, null);
    }

    /** 원문 대조에 쓰는 말: 원문(quote)이 있으면 그것, 없으면 대사. */
    public String source() {
      return quote == null || quote.isBlank() ? text : quote;
    }

    /** 대사와 원문 모두에 f를 적용한다(실명 바꾸기 등). */
    Line mapped(UnaryOperator<String> f) {
      return new Line(speaker, f.apply(text), quote == null ? null : f.apply(quote));
    }
  }

  /** 컷 수 하한. 기본은 4컷이다. */
  public static final int MIN_PANELS = 4;

  /** 컷 수 상한. 컷마다 그림 한 장이라 생성 시간이 컷 수에 비례해 늘어난다. */
  public static final int MAX_PANELS = 10;

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

  /** 등장인물만 바꾼 콘티. */
  public ComicScript withCharacters(List<Character> cast) {
    return new ComicScript(title, cast, panels, reviewed, nameWarnings, concept);
  }

  /** 화자 표시 → 말풍선 앞에 붙는 호칭(호칭이 없는 예전 콘티는 이름). */
  public Map<String, String> callNames() {
    return characters.stream()
        .collect(
            Collectors.toMap(Character::speaker, Character::callName, (a, b) -> a, HashMap::new));
  }

  /** 호칭으로 쓰기에 너무 길면 버린다(이름으로 부른다). */
  static final int MAX_ROLE_CHARS = 8;

  /**
   * 대사·제목에서 인물을 이름 대신 호칭(role)으로 부른다. 이름은 만화 아래 등장인물 소개에만 쓴다. 호칭은 다듬고, 겹치면 뒤 인물에 번호를 붙인다("친구",
   * "친구2"). 대사(text)와 제목 속 이름은 호칭으로 바꾸고 조사를 맞춘다("하늘아" → "할머니", "하늘이가" → "손녀가"). 원문(quote)은 대조용이라
   * 그대로 둔다. 호칭이 없는 인물은 그대로다.
   */
  public ComicScript withRoleCalls() {
    if (characters == null) {
      return this;
    }
    Set<String> used = new HashSet<>();
    List<Character> cast = new java.util.ArrayList<>();
    Map<String, String> calls = new java.util.LinkedHashMap<>();
    for (Character c : characters) {
      String role = cleanRole(c.role(), c.speaker());
      if (role != null) {
        String base = role;
        for (int n = 2; !used.add(role); n++) {
          role = base + n;
        }
        if (!c.displayName().equals(c.speaker())) {
          calls.put(c.displayName(), role);
        }
      }
      cast.add(java.util.Objects.equals(role, c.role()) ? c : c.withRole(role));
    }
    UnaryOperator<String> f = t -> CastNames.toRoles(t, calls);
    List<Panel> fixed =
        calls.isEmpty()
            ? panels
            : mapLines(l -> new Line(l.speaker(), f.apply(l.text()), l.quote()));
    String called = calls.isEmpty() ? title : f.apply(title);
    if (cast.equals(characters)
        && fixed.equals(panels)
        && java.util.Objects.equals(called, title)) {
      return this;
    }
    return new ComicScript(called, cast, fixed, reviewed, nameWarnings, concept);
  }

  /** 호칭을 다듬는다. 비었거나 길거나, 화자 표시("A")나 영문 한 글자라 호칭이 아니면 null(이름으로 부른다). */
  static String cleanRole(String role, String speaker) {
    if (role == null) {
      return null;
    }
    String r = role.strip().replaceAll("[\\s\"'.,:!?~]+$", "").strip();
    if (r.isEmpty() || r.length() > MAX_ROLE_CHARS || r.matches("[A-Za-z]") || r.equals(speaker)) {
      return null;
    }
    return r;
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
    if (characters == null
        || characters.isEmpty()
        || panels == null
        || panels.size() < MIN_PANELS
        || panels.size() > MAX_PANELS) {
      return "등장인물이 없거나 컷이 " + MIN_PANELS + "~" + MAX_PANELS + "개가 아니다";
    }
    String cast = castViolation();
    if (cast != null) {
      return cast;
    }
    Set<String> speakers = Set.copyOf(characters.stream().map(Character::speaker).toList());
    String flow = roleViolation();
    if (flow != null) {
      return flow;
    }
    for (int i = 0; i < panels.size(); i++) {
      Panel p = panels.get(i);
      if (p.scene() == null || p.scene().isBlank()) {
        return (i + 1) + "번 컷의 장면 설명이 없다";
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

  /**
   * 컷 역할 흐름: 첫 컷은 기, 마지막 컷은 결, 기·승·전·결이 모두 나오고 순서가 거꾸로 가지 않는다. 컷이 4개면 기·승·전·결 한 컷씩이고, 더 많으면 승이나 전이
   * 여러 컷이 된다(예: 6컷 기·승·승·전·전·결).
   */
  private String roleViolation() {
    int last = -1;
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < panels.size(); i++) {
      int r = ROLES.indexOf(panels.get(i).role());
      if (r < 0 || r < last) {
        return (i + 1) + "번 컷의 역할이 틀렸다(기·승·전·결 순서)";
      }
      last = r;
      seen.add(ROLES.get(r));
    }
    if (!"기".equals(panels.getFirst().role()) || !"결".equals(panels.getLast().role())) {
      return "첫 컷은 기, 마지막 컷은 결이어야 한다";
    }
    if (seen.size() != ROLES.size()) {
      return "기·승·전·결 중 빠진 역할이 있다";
    }
    if (panels.stream().filter(p -> "결".equals(p.role())).count() > 1
        || panels.stream().filter(p -> "기".equals(p.role())).count() > 2) {
      return "기는 두 컷, 결은 한 컷까지다";
    }
    return null;
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

  /** 모든 컷이 서로 다른 순간인지: 구도가 모두 같거나, 같은 대사가 두 번 나오거나, 장면 단어가 거의 같은 컷이 있으면 위반. */
  private String repetition() {
    if (panels.stream().map(Panel::shot).distinct().count() == 1) {
      return "모든 컷의 구도가 같다";
    }
    Set<String> said = new HashSet<>();
    for (Panel p : panels) {
      for (Line l : p.lines() == null ? List.<Line>of() : p.lines()) {
        if (!said.add(l.text().replaceAll("\\s+", ""))) {
          return "같은 대사가 두 번 나온다: " + l.text();
        }
      }
    }
    for (int i = 0; i < panels.size(); i++) {
      for (int j = i + 1; j < panels.size(); j++) {
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

  /** 코드가 고른 가명인지. 가명은 무작위라 같은 이름의 캐릭터 DB 인물(유진, 도담 등)과 이어 붙이면 안 된다. */
  public static boolean pseudonym(String name) {
    return name != null && CastNames.POOL.contains(name.strip());
  }

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
                  return c.withName(name);
                })
            .toList();
    List<Panel> fixed = mapLines(l -> l.mapped(t -> replaceAll(t, rename)));
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
                        ? c.withName(rename.get(c.displayName()))
                        : c)
            .toList();
    List<Panel> fixed = mapLines(l -> l.mapped(t -> replaceAll(t, rename)));
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
    List<Panel> fixed = out.mapLines(l -> l.mapped(ComicScript::stripJson));
    return new ComicScript(out.title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 모든 대사의 원문을 f로 바꾼다(원문 대조). 원문(quote)이 없는 대사는 대사 자체를 바꾼다. */
  public ComicScript withLineTexts(UnaryOperator<String> f) {
    return new ComicScript(
        title,
        characters,
        mapLines(
            l ->
                l.quote() == null || l.quote().isBlank()
                    ? new Line(l.speaker(), f.apply(l.text()), null)
                    : new Line(l.speaker(), l.text(), f.apply(l.quote()))),
        reviewed,
        nameWarnings,
        concept);
  }

  /** 컨셉 말투로 다듬은 대사가 비었거나 말풍선 길이(maxChars)를 넘으면 원문으로 되돌린다. 다듬기에 실패해도 말풍선이 비거나 넘치지 않게 한다. */
  public ComicScript withStyledLines(int maxChars) {
    return new ComicScript(
        title,
        characters,
        mapLines(
            l -> {
              String styled = l.text() == null ? "" : l.text().strip();
              boolean usable = !styled.isEmpty() && styled.length() <= maxChars;
              return usable || l.quote() == null || l.quote().isBlank()
                  ? l
                  : new Line(l.speaker(), l.quote().strip(), l.quote());
            }),
        reviewed,
        nameWarnings,
        concept);
  }

  /** 원문(quote, 없으면 대사)이 keep을 통과한 대사만 남긴다(장면 밖 대화에서 가져온 대사를 버린다). */
  public ComicScript withLinesWhere(Predicate<String> keep) {
    List<Panel> fixed =
        panels == null
            ? List.of()
            : panels.stream()
                .map(
                    p ->
                        p.withLines(
                            p.lines() == null
                                ? null
                                : p.lines().stream().filter(l -> keep.test(l.source())).toList()))
                .toList();
    return new ComicScript(title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /**
   * 대사 화자를 원문 화자로 맞춘다. 원문(quote)이 장면 발화 중 한 화자의 말과만 맞으면 그 화자로 바꾸고, 그 화자가 등장인물에 없으면 대사를 뺀다. LLM이 세
   * 번째 화자(시식 아주머니)의 말을 엄마 대사로 넣어 말풍선 주인이 틀렸다(funny-12). 여러 화자가 한 말("응")이나 원문에 없는 말은 그대로 둔다. 비게 된 컷은
   * {@link #withEmptyPanelsFilled}가 채운다.
   *
   * @param spoken 장면 구간 발화. speaker는 화자 표시
   */
  public ComicScript withSourceSpeakers(List<Line> spoken) {
    if (panels == null || characters == null || spoken == null || spoken.isEmpty()) {
      return this;
    }
    Set<String> cast = new HashSet<>(characters.stream().map(Character::speaker).toList());
    List<Panel> fixed = new java.util.ArrayList<>();
    for (Panel p : panels) {
      if (p.lines() == null) {
        fixed.add(p);
        continue;
      }
      List<Line> lines = new java.util.ArrayList<>();
      for (Line l : p.lines()) {
        String who = sourceSpeaker(l.source(), spoken);
        if (who == null || who.equals(l.speaker())) {
          lines.add(l);
        } else if (cast.contains(who)) {
          lines.add(new Line(who, l.text(), l.quote()));
        }
      }
      fixed.add(lines.equals(p.lines()) ? p : withSpeakersInScene(p.withLines(lines)));
    }
    return fixed.equals(panels)
        ? this
        : new ComicScript(title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 원문이 등장인물에 없는 화자의 말인 대사들의 화자(정렬). 없으면 빈 목록. */
  public List<String> uncastSourceSpeakers(List<Line> spoken) {
    if (panels == null || characters == null || spoken == null) {
      return List.of();
    }
    Set<String> cast = new HashSet<>(characters.stream().map(Character::speaker).toList());
    return panels.stream()
        .flatMap(
            p -> p.lines() == null ? java.util.stream.Stream.<Line>empty() : p.lines().stream())
        .map(l -> sourceSpeaker(l.source(), spoken))
        .filter(w -> w != null && !cast.contains(w))
        .distinct()
        .sorted()
        .toList();
  }

  /** 말(첫 문장)이 나온 장면 발화의 화자. 한 화자의 말에서만 나오면 그 화자, 없거나 여러 화자면 null. */
  private static String sourceSpeaker(String text, List<Line> spoken) {
    List<String> parts = text == null ? List.of() : SpokenLines.sentences(text);
    if (parts.isEmpty() || SpokenLines.shortReply(parts.get(0))) {
      return null;
    }
    Set<String> who = new HashSet<>();
    for (Line u : spoken) {
      if (u.speaker() != null
          && u.text() != null
          && SpokenLines.indexIn(text, List.of(u.text())) == 0) {
        who.add(u.speaker());
      }
    }
    return who.size() == 1 ? who.iterator().next() : null;
  }

  /**
   * 앞 컷에서 이미 나온 대사를 뒤 컷에서 뺀다(공백 무시). 실제 대화가 같은 말을 되풀이하면 대사가 같은 발화로 맞춰져 "같은 대사가 두 번 나온다" 위반이 되고, 다시
   * 써도 같은 결과라 만화가 실패했다(mystery-04). 비게 된 컷은 {@link #withEmptyPanelsFilled}가 채운다.
   */
  public ComicScript withoutRepeatedLines() {
    if (panels == null) {
      return this;
    }
    Set<String> said = new HashSet<>();
    List<Panel> fixed =
        panels.stream()
            .map(
                p ->
                    p.lines() == null
                        ? p
                        : p.withLines(
                            p.lines().stream()
                                .filter(
                                    l ->
                                        l.text() == null
                                            || said.add(l.text().replaceAll("\\s+", "")))
                                .toList()))
            .toList();
    return new ComicScript(title, characters, fixed, reviewed, nameWarnings, concept);
  }

  /**
   * 대사가 하나도 없는 컷(LLM이 비웠거나 장면 밖 대사를 버려 비게 된 컷)을 장면 대화의 실제 발화로 채운다. 앞뒤 컷 대사 사이에 한 말 중 아직 쓰지 않은 말을
   * 고르고(맞장구보다 긴 말을 먼저), 그 사이에 없으면 장면 전체에서 고른다. 채울 말이 없으면 그대로 둔다.
   *
   * @param spoken 장면 구간 발화(시간 순서). speaker는 화자 표시
   */
  public ComicScript withEmptyPanelsFilled(List<Line> spoken) {
    if (panels == null || characters == null || spoken == null || spoken.isEmpty()) {
      return this;
    }
    Set<String> speakers = new HashSet<>(characters.stream().map(Character::speaker).toList());
    List<Line> pool =
        spoken.stream()
            .filter(l -> l.text() != null && !l.text().isBlank() && speakers.contains(l.speaker()))
            .toList();
    List<String> texts = pool.stream().map(Line::text).toList();
    Set<String> said = new HashSet<>();
    panels.stream()
        .flatMap(
            p -> p.lines() == null ? java.util.stream.Stream.<Line>empty() : p.lines().stream())
        .forEach(l -> said.add(key(l.source())));
    List<Panel> out = new java.util.ArrayList<>(panels);
    for (int i = 0; i < out.size(); i++) {
      Panel p = out.get(i);
      if (p.lines() != null && !p.lines().isEmpty()) {
        continue;
      }
      int from = lastIndexBefore(out, i, texts);
      int to = firstIndexAfter(out, i, texts, pool.size());
      Line pick = pick(pool, from, to, said);
      if (pick == null) {
        pick = pick(pool, -1, pool.size(), said);
      }
      if (pick != null) {
        said.add(key(pick.text()));
        Line filled = new Line(pick.speaker(), pick.text(), pick.text());
        out.set(i, withSpeakersInScene(p.withLines(List.of(filled))));
      }
    }
    return new ComicScript(title, characters, out, reviewed, nameWarnings, concept);
  }

  /** (from, to) 사이의 아직 안 쓴 말 중 가장 이른 것(앞 컷 바로 다음 순간). 맞장구가 아닌 말을 먼저 고른다. */
  private static Line pick(List<Line> pool, int from, int to, Set<String> said) {
    int lo = from + 1;
    int hi = Math.min(to, pool.size());
    if (lo >= hi) {
      return null;
    }
    List<Line> range =
        pool.subList(lo, hi).stream().filter(l -> !said.contains(key(l.text()))).toList();
    List<Line> longer = range.stream().filter(l -> !SpokenLines.shortReply(l.text())).toList();
    List<Line> choice = longer.isEmpty() ? range : longer;
    return choice.isEmpty() ? null : choice.get(0);
  }

  private static int lastIndexBefore(List<Panel> ps, int i, List<String> texts) {
    int at = -1;
    for (int k = 0; k < i; k++) {
      for (Line l : ps.get(k).lines() == null ? List.<Line>of() : ps.get(k).lines()) {
        at = Math.max(at, SpokenLines.indexIn(l.source(), texts));
      }
    }
    return at;
  }

  private static int firstIndexAfter(List<Panel> ps, int i, List<String> texts, int none) {
    for (int k = i + 1; k < ps.size(); k++) {
      int best = none;
      for (Line l : ps.get(k).lines() == null ? List.<Line>of() : ps.get(k).lines()) {
        int at = SpokenLines.indexIn(l.source(), texts);
        if (at >= 0) {
          best = Math.min(best, at);
        }
      }
      if (best < none) {
        return best;
      }
    }
    return none;
  }

  private static String key(String text) {
    return text == null ? "" : text.replaceAll("\\s+", "");
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
      cast.add(c.withLook(animals.get(i % animals.size()) + rest));
    }
    return new ComicScript(title, cast, panels, reviewed, nameWarnings, concept);
  }

  /**
   * 그림 단계에서 캐릭터 DB의 캐릭터를 찾은 인물은 그 캐릭터 이름을 쓴다. 등장인물 이름과 제목·대사·원문 속 가명을 함께 바꾸고 조사는 새 이름의 받침에 맞춘다. 다른
   * 인물이 이미 쓰는 이름은 겹치므로 바꾸지 않는다.
   *
   * @param names 화자 표시(A, B…) → 캐릭터 이름
   */
  public ComicScript withCharacterNames(Map<String, String> names) {
    if (names.isEmpty() || characters == null) {
      return this;
    }
    Map<String, String> rename = new java.util.LinkedHashMap<>();
    Set<String> taken = new HashSet<>();
    for (Character c : characters) {
      String to = names.get(c.speaker());
      if (to == null || to.isBlank() || to.strip().equals(c.displayName())) {
        taken.add(c.displayName());
      }
    }
    List<Character> cast = new java.util.ArrayList<>();
    for (Character c : characters) {
      String to = names.get(c.speaker());
      String from = c.displayName();
      if (to == null || to.isBlank() || to.strip().equals(from) || !taken.add(to.strip())) {
        cast.add(c);
        continue;
      }
      if (!from.equals(c.speaker())) {
        rename.put(from, to.strip());
      }
      cast.add(c.withName(to.strip()));
    }
    if (rename.isEmpty() && cast.equals(characters)) {
      return this;
    }
    UnaryOperator<String> f = t -> CastNames.rename(t, rename);
    return new ComicScript(
        f.apply(title), cast, mapLines(l -> l.mapped(f)), reviewed, nameWarnings, concept);
  }

  /** 대사와 제목의 단어를 바꾼다(긴 단어부터). */
  public ComicScript replaced(Map<String, String> rename) {
    if (rename.isEmpty()) {
      return this;
    }
    List<Panel> fixed = mapLines(l -> l.mapped(t -> replaceAll(t, rename)));
    return new ComicScript(
        replaceAll(title, rename), characters, fixed, reviewed, nameWarnings, concept);
  }

  /** 모든 컷의 대사를 f로 바꾼 새 컷 목록. 컷이 없으면 빈 목록(검증에서 컷 수 위반이 된다). */
  private List<Panel> mapLines(UnaryOperator<Line> f) {
    if (panels == null) {
      return List.of();
    }
    return panels.stream()
        .map(p -> p.withLines(p.lines() == null ? null : p.lines().stream().map(f).toList()))
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
                    withoutSpeakerTag(l.text()),
                    l.quote()));
    return new ComicScript(
        title,
        characters,
        fixed.stream().map(ComicScript::withSpeakersInScene).toList(),
        reviewed,
        nameWarnings,
        concept);
  }

  /** 대사 앞에 LLM이 붙인 화자 표시("A: ", "{B}：")를 뗀다. */
  static String withoutSpeakerTag(String text) {
    return text == null ? null : text.replaceFirst("^\\s*\\{?[A-Z]}?\\s*[:：]\\s*", "");
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
    return p.withScene(scene.toString());
  }

  ComicScript reviewedWith(List<String> warnings) {
    return new ComicScript(
        title, characters, panels, true, warnings == null ? List.of() : warnings, concept);
  }
}
