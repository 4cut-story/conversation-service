package net.java21.fourcut.conversation.sound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 라이브 모드 컷별 배경음 고르기 (docs specs/003-live-mode R6). 컷마다 소리 장면 문장·장소·대사를 설명 임베딩(BGE-M3)으로
 * 장소·환경음(AMBIENCE)과 견줘 가까운 소리를 고른다. 소리의 장소 낱말이 소리 장면 문장에 있으면 앞으로 올리고, 앞 컷과 같은 장소이고 앞 소리가 후보 위쪽에 있으면
 * 앞 소리를 이어 쓴다(끊지 않고 이어 틀기). 임베딩을 쓸 수 없으면 글자로 찾고, 그래도 없으면 소리 없음이다. 고르다 실패해도 만화 생성은 이어진다. 콘티가 컷에 사건
 * 소리(알람, 초인종…)를 적었으면 효과음(EFFECT)에서 가장 가까운 소리 하나를 따로 고른다({@link #effects}).
 */
@Service
public class SoundMatcher {

  private static final Logger log = LoggerFactory.getLogger(SoundMatcher.class);

  /** 컷마다 받는 후보 수. */
  static final int CANDIDATES = 10;

  /** 앞 소리를 이어 쓸 수 있는 후보 순위(이 안에 있으면 이어 쓴다). */
  static final int KEEP_WITHIN = 3;

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

  /** 가까운 순 후보에서 장소 낱말이 소리 장면에 들어 있는 소리를 앞으로(같은 무리 안에서는 원래 순서). */
  List<SoundLibrary.Sound> ranked(Query q) {
    List<SoundLibrary.Sound> found = find(q);
    String scene = text(q.soundScene());
    List<SoundLibrary.Sound> placeMatch = new ArrayList<>();
    List<SoundLibrary.Sound> rest = new ArrayList<>();
    for (SoundLibrary.Sound s : found) {
      boolean hit =
          !scene.isEmpty()
              && s.places() != null
              && s.places().stream().anyMatch(p -> p.length() >= 2 && scene.contains(p));
      (hit ? placeMatch : rest).add(s);
    }
    placeMatch.addAll(rest);
    return placeMatch;
  }

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

  /** 질의 글: 소리 장면, 장소, 대사 순서. */
  static String query(Query q) {
    String joined =
        Stream.of(q.soundScene(), q.place(), q.lines())
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
