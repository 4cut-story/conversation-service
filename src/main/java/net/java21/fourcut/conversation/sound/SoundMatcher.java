package net.java21.fourcut.conversation.sound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 라이브 모드 컷별 배경음 고르기 (docs specs/003-live-mode R6). 컷마다 소리 장면 문장·장소·대사를 설명 임베딩(BGE-M3)으로
 * 장소·환경음(AMBIENCE)과 견줘 가까운 소리를 고른다. 소리의 장소 낱말이 소리 장면 문장에 있으면 가까운 위쪽 몇 개 안에서만 앞으로 올리고, 시간대·날씨가 어긋나는
 * 소리는 뒤로 보내며, 앞 컷과 같은 장소이고 앞 소리가 후보 위쪽에 있으면 앞 소리를 이어 쓴다(끊지 않고 이어 틀기). 임베딩을 쓸 수 없으면 글자로 찾고, 그래도 없으면
 * 소리 없음이다. 고르다 실패해도 만화 생성은 이어진다. 콘티가 컷에 사건 소리(알람, 초인종…)를 적었으면 효과음(EFFECT)에서 가장 가까운 소리 하나를 따로
 * 고른다({@link #effects}).
 */
@Service
public class SoundMatcher {

  private static final Logger log = LoggerFactory.getLogger(SoundMatcher.class);

  /** 컷마다 받는 후보 수. */
  static final int CANDIDATES = 10;

  /** 앞 소리를 이어 쓸 수 있는 후보 순위(이 안에 있으면 이어 쓴다). */
  static final int KEEP_WITHIN = 3;

  /** 장소 낱말로 앞으로 올릴 수 있는 가까운 후보 수. */
  static final int PLACE_WINDOW = 3;

  /** 질의 글 최대 길이. 대사가 길어도 장소·소리 장면이 묻히지 않게 자른다. */
  static final int MAX_QUERY = 300;

  /** 컷 하나의 질의. place는 그림용 배경 문구의 첫 마디(장소 이름), lines는 대사를 이은 글. */
  public record Query(String soundScene, String place, String lines) {}

  /** 고른 소리와 저작자 표시. */
  public record Picked(
      String publicId,
      String title,
      String author,
      String license,
      String licenseUrl,
      String sourceUrl,
      String originalTitle,
      Double score,
      String kind) {

    /** 점수를 모르는 소리. */
    public Picked(
        String publicId,
        String title,
        String author,
        String license,
        String licenseUrl,
        String sourceUrl,
        String originalTitle) {
      this(publicId, title, author, license, licenseUrl, sourceUrl, originalTitle, null, null);
    }

    /** 바탕 배경음악인지(장소·환경음보다 작게 깐다). */
    public boolean music() {
      return "MUSIC".equals(kind);
    }
  }

  /** 거리가 이만큼 안이면 같은 점수로 보고 만화마다 다르게 고른다(설명 임베딩 코사인 거리). */
  static final double TIE_MARGIN = 0.02;

  /**
   * 후보 하나와 질의까지의 거리(설명 임베딩 코사인 거리, 0~2). 거리를 모르는 찾기(글자 찾기)는 {@link #UNKNOWN}부터 순위를 더한 음수를 쓴다(같은 점수가
   * 없고, 거리 기준을 적용하지 않는다).
   */
  public record Scored(SoundLibrary.Sound sound, double distance) {

    boolean known() {
      return distance >= 0;
    }
  }

  /** 거리를 모르는 후보의 거리 시작값. */
  static final double UNKNOWN = -1_000_000;

  /**
   * 소리를 쓰는 거리 한도 기본값(코사인 거리 0.40 = 유사도 0.60 이상). 이보다 먼 소리는 장면과 관계가 약하다고 보고 그 컷은 소리 없이 둔다. 아무 소리나 가장
   * 가까운 것을 붙이면 장면과 상관없는 소리가 나왔다(marco, 2026-11-09). 실제 만화의 점수(재생 정보 sound.score)를 보고 조정한다.
   */
  public static final double DEFAULT_MAX_DISTANCE = 0.40;

  private final BiFunction<String, Integer, List<Scored>> finder;
  private final BiFunction<String, Integer, List<Scored>> effectFinder;
  private final BiFunction<String, Integer, List<Scored>> musicFinder;
  private final double maxDistance;

  @Autowired
  SoundMatcher(
      SoundLibrary library,
      @Value("${sound.max-distance:" + DEFAULT_MAX_DISTANCE + "}") double maxDistance) {
    this.finder =
        (q, n) -> library.scored(new SoundLibrary.Filter("AMBIENCE", null, null, null), q, n);
    this.effectFinder =
        (q, n) -> library.scored(new SoundLibrary.Filter("EFFECT", null, null, null), q, n);
    this.musicFinder =
        (q, n) -> library.scored(new SoundLibrary.Filter("MUSIC", null, null, null), q, n);
    this.maxDistance = maxDistance;
  }

  SoundMatcher(BiFunction<String, Integer, List<SoundLibrary.Sound>> finder) {
    this(finder, (q, n) -> List.of());
  }

  SoundMatcher(
      BiFunction<String, Integer, List<SoundLibrary.Sound>> finder,
      BiFunction<String, Integer, List<SoundLibrary.Sound>> effectFinder) {
    this(
        (q, n) -> byRank(finder.apply(q, n)),
        (q, n) -> byRank(effectFinder.apply(q, n)),
        (q, n) -> List.of(),
        DEFAULT_MAX_DISTANCE);
  }

  private SoundMatcher(
      BiFunction<String, Integer, List<Scored>> scored,
      BiFunction<String, Integer, List<Scored>> effectFinder,
      BiFunction<String, Integer, List<Scored>> musicFinder,
      double maxDistance) {
    this.finder = scored;
    this.effectFinder = effectFinder;
    this.musicFinder = musicFinder;
    this.maxDistance = maxDistance;
  }

  /** 거리를 아는 찾기로 만든다(테스트). */
  static SoundMatcher scored(BiFunction<String, Integer, List<Scored>> scored) {
    return scored(scored, (q, n) -> List.of());
  }

  /** 거리를 아는 배경음·효과음 찾기로 만든다(테스트). */
  static SoundMatcher scored(
      BiFunction<String, Integer, List<Scored>> scored,
      BiFunction<String, Integer, List<Scored>> effects) {
    return scored(scored, effects, (q, n) -> List.of());
  }

  /** 거리를 아는 배경음·효과음·배경음악 찾기로 만든다(테스트). */
  static SoundMatcher scored(
      BiFunction<String, Integer, List<Scored>> scored,
      BiFunction<String, Integer, List<Scored>> effects,
      BiFunction<String, Integer, List<Scored>> music) {
    return new SoundMatcher(scored, effects, music, DEFAULT_MAX_DISTANCE);
  }

  static List<Scored> byRank(List<SoundLibrary.Sound> sounds) {
    List<Scored> out = new ArrayList<>();
    for (int i = 0; i < sounds.size(); i++) {
      out.add(new Scored(sounds.get(i), UNKNOWN + i));
    }
    return out;
  }

  /** 거리 한도 안의 후보만. 거리를 모르는 후보(글자 찾기)는 그대로 둔다. */
  List<Scored> close(List<Scored> found) {
    return found.stream().filter(s -> !s.known() || s.distance() <= maxDistance).toList();
  }

  /** 컷마다 고른 사건 효과음(같은 순서·개수). 사건 소리가 비었거나 못 찾으면 빈 값. 앞 컷 소리를 이어 쓰지 않는다. */
  public List<Optional<Picked>> effects(List<String> events) {
    List<Optional<Picked>> out = new ArrayList<>();
    for (String event : events) {
      Scored chosen = null;
      String q = text(event);
      if (!q.isEmpty()) {
        try {
          List<Scored> found = close(effectFinder.apply(q, EFFECT_CANDIDATES));
          if (found.isEmpty()) {
            for (String word : words(q)) {
              found = close(effectFinder.apply(word, EFFECT_CANDIDATES));
              if (!found.isEmpty()) {
                break;
              }
            }
          }
          if (!found.isEmpty()) {
            SoundLibrary.Sound head = headFirst(q, found.stream().map(Scored::sound).toList());
            chosen = found.stream().filter(s -> s.sound() == head).findFirst().orElseThrow();
          }
        } catch (RuntimeException e) {
          log.warn("컷 효과음을 고르지 못했다: {}", e.toString());
        }
      }
      out.add(Optional.ofNullable(chosen).map(SoundMatcher::picked));
    }
    return out;
  }

  /**
   * 가까운 효과음 후보 중 사건 소리의 중심 낱말이 제목에 있는 소리를 고른다. 한국어 이름은 끝 낱말이 중심이라("휴대폰 알람 소리"는 알람) 끝 낱말을 2점, 나머지
   * 낱말을 1점으로 센다. "휴대폰 알람 소리"가 "휴대폰 진동"보다 "전자 알람시계"로 가게 한다. 점수가 같으면 가까운 순서.
   */
  static SoundLibrary.Sound headFirst(String event, List<SoundLibrary.Sound> found) {
    List<String> words = words(event).stream().filter(w -> !GENERIC.contains(w)).toList();
    SoundLibrary.Sound best = found.get(0);
    int bestScore = -1;
    for (SoundLibrary.Sound s : found) {
      String title = text(s.title());
      int score = 0;
      for (int i = 0; i < words.size(); i++) {
        if (title.contains(words.get(i))) {
          score += i == words.size() - 1 ? 2 : 1;
        }
      }
      if (score > bestScore) {
        best = s;
        bestScore = score;
      }
    }
    return best;
  }

  /** 효과음 후보 수. */
  static final int EFFECT_CANDIDATES = 5;

  private static final List<String> GENERIC = List.of("소리", "효과음", "울리는", "나는");

  /** 컷마다 고른 소리(같은 순서·개수). 고르지 못한 컷은 빈 값. 같은 점수 후보는 늘 첫 후보다. */
  public List<Optional<Picked>> match(List<Query> panels) {
    return match(panels, 0);
  }

  /**
   * 컷마다 고른 소리(같은 순서·개수). 고르지 못한 컷은 빈 값. 거리가 사실상 같은 후보({@link #TIE_MARGIN})가 여럿이면 seed(만화마다 정해진 값)로
   * 고른다. 늘 첫 후보만 쓰면 비슷한 장면마다 같은 소리가 나온다. 같은 만화는 같은 소리다.
   */
  public List<Optional<Picked>> match(List<Query> panels, long seed) {
    List<Optional<Picked>> out = new ArrayList<>();
    Scored previous = null;
    String previousPlace = null;
    for (Query q : panels) {
      Scored chosen = null;
      try {
        List<Scored> ranked = ranked(q);
        if (previous != null && samePlace(previousPlace, q.place())) {
          String keep = previous.sound().id();
          chosen =
              ranked.stream()
                  .limit(KEEP_WITHIN)
                  .filter(s -> s.sound().id().equals(keep))
                  .findFirst()
                  .orElse(null);
        }
        if (chosen == null && !ranked.isEmpty()) {
          chosen = tieBreak(q, ranked, seed);
        }
        if (chosen == null) {
          // 한도 안의 장소·환경음이 없으면 같은 질의로 분위기 배경음악을 깐다
          chosen = music(q, previous, seed);
        }
      } catch (RuntimeException e) {
        log.warn("컷 배경음을 고르지 못했다: {}", e.toString());
      }
      out.add(Optional.ofNullable(chosen).map(SoundMatcher::picked));
      previous = chosen;
      previousPlace = q.place();
    }
    return out;
  }

  /**
   * 바탕 배경음악. 한도 안의 장소·환경음이 없는 컷도 바탕 소리는 늘 깔리게, 분위기가 가장 가까운 음악을 거리 한도 없이 고른다(marco: 기본 배경음 위에 상황에 맞는
   * 효과음). 앞 컷이 같은 음악이고 후보 위쪽에 있으면 이어 쓴다. 음악이 없으면 null.
   */
  private Scored music(Query q, Scored previous, long seed) {
    String query = query(q);
    if (query.isEmpty()) {
      return null;
    }
    List<Scored> found = musicFinder.apply(query, CANDIDATES);
    if (found.isEmpty()) {
      return null;
    }
    if (previous != null) {
      String keep = previous.sound().id();
      Optional<Scored> same =
          found.stream().limit(KEEP_WITHIN).filter(s -> s.sound().id().equals(keep)).findFirst();
      if (same.isPresent()) {
        return same.get();
      }
    }
    Scored best = found.get(0);
    List<Scored> tied =
        found.stream().filter(s -> s.distance() - best.distance() <= TIE_MARGIN).toList();
    return tied.get((int) Math.floorMod(seed, (long) tied.size()));
  }

  /** 첫 후보와 장소 낱말 여부·어긋남이 같고 거리 차이가 {@link #TIE_MARGIN} 안인 후보들 중 seed로 하나. */
  static Scored tieBreak(Query q, List<Scored> ranked, long seed) {
    String scene = text(q.soundScene());
    String context = (scene + " " + text(q.place())).toLowerCase(Locale.ROOT);
    Scored best = ranked.get(0);
    boolean hit = placeHit(scene, best.sound());
    boolean off = conflicts(context, best.sound());
    List<Scored> tied =
        ranked.stream()
            .filter(s -> Math.abs(s.distance() - best.distance()) <= TIE_MARGIN)
            .filter(s -> placeHit(scene, s.sound()) == hit)
            .filter(s -> conflicts(context, s.sound()) == off)
            .toList();
    return tied.get((int) Math.floorMod(seed, (long) tied.size()));
  }

  /**
   * 가까운 순 후보를 장면에 맞게 다시 줄 세운다. 시간대(밤·낮)나 날씨(비)가 소리 장면과 어긋나는 소리는 맨 뒤로 보낸다. 장소 낱말이 소리 장면에 있는 소리는 가까운
   * 위쪽 {@link #PLACE_WINDOW}개 안에서만 앞으로 올린다. 장소 낱말 하나 때문에 들리는 소리가 다른 후보(마트 장면의 와인병 부딪히는 소리)가 위로
   * 뛰어오르지 않게 한다.
   */
  List<Scored> ranked(Query q) {
    List<Scored> found = find(q);
    String scene = text(q.soundScene());
    String context = (scene + " " + text(q.place())).toLowerCase(Locale.ROOT);
    List<Scored> fit = new ArrayList<>();
    List<Scored> off = new ArrayList<>();
    for (Scored s : close(found)) {
      (conflicts(context, s.sound()) ? off : fit).add(s);
    }
    List<Scored> top = fit.subList(0, Math.min(PLACE_WINDOW, fit.size()));
    List<Scored> out = new ArrayList<>();
    top.stream().filter(s -> placeHit(scene, s.sound())).forEach(out::add);
    top.stream().filter(s -> !placeHit(scene, s.sound())).forEach(out::add);
    out.addAll(fit.subList(top.size(), fit.size()));
    // 시간대·날씨가 어긋나는 소리는 쓰지 않는다(장면과 관계없는 소리보다 소리 없음이나 배경음악이 낫다)
    return out;
  }

  private static boolean placeHit(String scene, SoundLibrary.Sound s) {
    return !scene.isEmpty()
        && s.places() != null
        && s.places().stream().anyMatch(p -> p.length() >= 2 && scene.contains(p));
  }

  /** 소리가 장면의 시간대·날씨와 어긋나는가. 장면이 밤·낮이나 비를 말하지 않으면 어긋나지 않는다(비는 장면에 없는데 소리에만 있으면 어긋난다). */
  static boolean conflicts(String context, SoundLibrary.Sound s) {
    String sound =
        (text(s.title()) + " " + text(s.description()) + " " + text(s.category()))
            .toLowerCase(Locale.ROOT);
    boolean sceneNight = NIGHT.matcher(context).find();
    boolean sceneDay = DAY.matcher(context).find();
    boolean soundNight = NIGHT.matcher(sound).find();
    boolean soundDay = DAY.matcher(sound).find();
    boolean dayVsNight = sceneDay && !sceneNight && soundNight && !soundDay;
    boolean nightVsDay = sceneNight && !sceneDay && soundDay && !soundNight;
    boolean rainOnly = RAIN.matcher(sound).find() && !RAIN.matcher(context).find();
    return dayVsNight || nightVsDay || rainOnly;
  }

  private static final Pattern NIGHT = Pattern.compile("밤|한밤|새벽|풀벌레|귀뚜라미|night|midnight");
  private static final Pattern DAY =
      Pattern.compile("아침|한낮|낮 |낮에|낮의|오전|오후|대낮|morning|daytime|noon|afternoon");
  private static final Pattern RAIN =
      Pattern.compile("빗|비가|비 오|비오|비 내|비를|소나기|장맛비|폭우|천둥|우산|rain|thunder|storm");

  private List<Scored> find(Query q) {
    String query = query(q);
    if (query.isEmpty()) {
      return List.of();
    }
    List<Scored> found = finder.apply(query, CANDIDATES);
    if (!found.isEmpty()) {
      return found;
    }
    // 임베딩이 없으면 글자로 찾는다: 긴 문장은 맞는 소리가 없으니 소리 장면 낱말 하나씩
    for (String word : words(q.soundScene())) {
      found = finder.apply(word, CANDIDATES);
      if (!found.isEmpty()) {
        return found;
      }
    }
    return List.of();
  }

  /** 질의 글: 소리 장면, 장소 순서. 대사는 소리 장면이 없을 때만 넣는다(대사의 음식·물건 낱말이 들리는 소리를 흐리지 않게). */
  static String query(Query q) {
    String lines = text(q.soundScene()).isEmpty() ? q.lines() : null;
    String joined =
        Stream.of(q.soundScene(), q.place(), lines)
            .map(SoundMatcher::text)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.joining(". "));
    return joined.length() > MAX_QUERY ? joined.substring(0, MAX_QUERY) : joined;
  }

  static List<String> words(String text) {
    return Arrays.stream(text(text).split("[\\s,.·!?]+"))
        .map(String::strip)
        .filter(w -> w.length() >= 2)
        .toList();
  }

  private static boolean samePlace(String a, String b) {
    return !text(a).isEmpty() && text(a).equalsIgnoreCase(text(b));
  }

  private static Picked picked(Scored scored) {
    SoundLibrary.Sound s = scored.sound();
    SoundLibrary.Credit c = s.credit();
    return new Picked(
        s.id(),
        s.title(),
        c == null ? null : c.author(),
        c == null ? null : c.license(),
        c == null ? null : c.licenseUrl(),
        c == null ? null : c.sourceUrl(),
        c == null ? null : c.originalTitle(),
        scored.known() ? Math.round((1 - scored.distance()) * 100) / 100.0 : null,
        s.kind());
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }
}
