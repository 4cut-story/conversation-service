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
      String originalTitle) {}

  private final BiFunction<String, Integer, List<SoundLibrary.Sound>> finder;
  private final BiFunction<String, Integer, List<SoundLibrary.Sound>> effectFinder;

  @Autowired
  SoundMatcher(SoundLibrary library) {
    this(
        (q, n) -> library.search(new SoundLibrary.Filter("AMBIENCE", null, null, null), q, n),
        (q, n) -> library.search(new SoundLibrary.Filter("EFFECT", null, null, null), q, n));
  }

  SoundMatcher(BiFunction<String, Integer, List<SoundLibrary.Sound>> finder) {
    this(finder, (q, n) -> List.of());
  }

  SoundMatcher(
      BiFunction<String, Integer, List<SoundLibrary.Sound>> finder,
      BiFunction<String, Integer, List<SoundLibrary.Sound>> effectFinder) {
    this.finder = finder;
    this.effectFinder = effectFinder;
  }

  /** 컷마다 고른 사건 효과음(같은 순서·개수). 사건 소리가 비었거나 못 찾으면 빈 값. 앞 컷 소리를 이어 쓰지 않는다. */
  public List<Optional<Picked>> effects(List<String> events) {
    List<Optional<Picked>> out = new ArrayList<>();
    for (String event : events) {
      SoundLibrary.Sound chosen = null;
      String q = text(event);
      if (!q.isEmpty()) {
        try {
          List<SoundLibrary.Sound> found = effectFinder.apply(q, 1);
          if (found.isEmpty()) {
            for (String word : words(q)) {
              found = effectFinder.apply(word, 1);
              if (!found.isEmpty()) {
                break;
              }
            }
          }
          chosen = found.isEmpty() ? null : found.get(0);
        } catch (RuntimeException e) {
          log.warn("컷 효과음을 고르지 못했다: {}", e.toString());
        }
      }
      out.add(Optional.ofNullable(chosen).map(SoundMatcher::picked));
    }
    return out;
  }

  /** 컷마다 고른 소리(같은 순서·개수). 고르지 못한 컷은 빈 값. */
  public List<Optional<Picked>> match(List<Query> panels) {
    List<Optional<Picked>> out = new ArrayList<>();
    SoundLibrary.Sound previous = null;
    String previousPlace = null;
    for (Query q : panels) {
      SoundLibrary.Sound chosen = null;
      try {
        List<SoundLibrary.Sound> ranked = ranked(q);
        if (previous != null && samePlace(previousPlace, q.place())) {
          String keep = previous.id();
          if (ranked.stream().limit(KEEP_WITHIN).anyMatch(s -> s.id().equals(keep))) {
            chosen = previous;
          }
        }
        if (chosen == null && !ranked.isEmpty()) {
          chosen = ranked.get(0);
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
   * 가까운 순 후보를 장면에 맞게 다시 줄 세운다. 시간대(밤·낮)나 날씨(비)가 소리 장면과 어긋나는 소리는 맨 뒤로 보낸다. 장소 낱말이 소리 장면에 있는 소리는 가까운
   * 위쪽 {@link #PLACE_WINDOW}개 안에서만 앞으로 올린다. 장소 낱말 하나 때문에 들리는 소리가 다른 후보(마트 장면의 와인병 부딪히는 소리)가 위로
   * 뛰어오르지 않게 한다.
   */
  List<SoundLibrary.Sound> ranked(Query q) {
    List<SoundLibrary.Sound> found = find(q);
    String scene = text(q.soundScene());
    String context = (scene + " " + text(q.place())).toLowerCase(Locale.ROOT);
    List<SoundLibrary.Sound> fit = new ArrayList<>();
    List<SoundLibrary.Sound> off = new ArrayList<>();
    for (SoundLibrary.Sound s : found) {
      (conflicts(context, s) ? off : fit).add(s);
    }
    List<SoundLibrary.Sound> top = fit.subList(0, Math.min(PLACE_WINDOW, fit.size()));
    List<SoundLibrary.Sound> out = new ArrayList<>();
    top.stream().filter(s -> placeHit(scene, s)).forEach(out::add);
    top.stream().filter(s -> !placeHit(scene, s)).forEach(out::add);
    out.addAll(fit.subList(top.size(), fit.size()));
    out.addAll(off);
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
    if (sceneDay && !sceneNight && soundNight && !soundDay) {
      return true;
    }
    if (sceneNight && !sceneDay && soundDay && !soundNight) {
      return true;
    }
    return RAIN.matcher(sound).find() && !RAIN.matcher(context).find();
  }

  private static final Pattern NIGHT = Pattern.compile("밤|한밤|새벽|풀벌레|귀뚜라미|night|midnight");
  private static final Pattern DAY =
      Pattern.compile("아침|한낮|낮 |낮에|낮의|오전|오후|대낮|morning|daytime|noon|afternoon");
  private static final Pattern RAIN =
      Pattern.compile("빗|비가|비 오|비오|비 내|비를|소나기|장맛비|폭우|천둥|우산|rain|thunder|storm");

  private List<SoundLibrary.Sound> find(Query q) {
    String query = query(q);
    if (query.isEmpty()) {
      return List.of();
    }
    List<SoundLibrary.Sound> found = finder.apply(query, CANDIDATES);
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

  private static Picked picked(SoundLibrary.Sound s) {
    SoundLibrary.Credit c = s.credit();
    return new Picked(
        s.id(),
        s.title(),
        c == null ? null : c.author(),
        c == null ? null : c.license(),
        c == null ? null : c.licenseUrl(),
        c == null ? null : c.sourceUrl(),
        c == null ? null : c.originalTitle());
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }
}
