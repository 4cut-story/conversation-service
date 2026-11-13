package net.java21.fourcut.conversation.sound;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.java21.fourcut.conversation.character.TextEmbedder;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.scenario.StoryMood;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.support.Cursor;
import net.java21.fourcut.conversation.support.CursorPage;
import net.java21.fourcut.conversation.support.PublicId;
import net.java21.fourcut.conversation.support.Vectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 배경음 둘러보기와 찾기. 목록은 종류·카테고리·분위기로 거르고, 검색어가 있으면 설명 임베딩이 가까운 순으로 준다(임베딩 서버나 pgvector가 없으면 제목·설명·장소
 * 글자로 찾는다). 상세는 소리 임베딩이 가까운 소리를 함께 준다. 미리 듣기는 사전 서명 GET 주소로 준다.
 */
@Service
class SoundLibrary {

  /** 미리 듣기 주소 유효 시간. web이 받자마자 바이트를 가져간다. */
  static final Duration AUDIO_TTL = Duration.ofMinutes(5);

  /** 상세에 붙이는 비슷한 소리 수. */
  static final int SIMILAR = 6;

  private static final String COLUMNS =
      "s.id, s.public_id, s.kind, s.category, s.title, s.description, s.moods, s.places,"
          + " s.duration_ms, s.source, s.source_url, s.original_title, s.author, s.license,"
          + " s.license_url";

  private final JdbcClient jdbc;
  private final TextEmbedder embedder;
  private final ObjectStorage storage;

  /** vector 타입의 스키마. 컬럼이 없으면(pgvector 없음) 빈 값. 처음 확인한 값을 계속 쓴다. */
  private final AtomicReference<Optional<String>> vectorSchema = new AtomicReference<>();

  SoundLibrary(JdbcClient jdbc, TextEmbedder embedder, ObjectStorage storage) {
    this.jdbc = jdbc;
    this.embedder = embedder;
    this.storage = storage;
  }

  record Sound(
      String id,
      String kind,
      String category,
      String title,
      String description,
      List<String> moods,
      List<String> places,
      int durationMs,
      Credit credit) {}

  /** 저작자 표시 (CC BY는 저작자·출처·라이선스를 보여 줘야 한다). */
  record Credit(
      String source,
      String sourceUrl,
      String originalTitle,
      String author,
      String license,
      String licenseUrl) {}

  record Detail(Sound sound, List<Sound> similar) {}

  record CategoryCount(String kind, String category, long count) {}

  record MoodCount(String mood, long count) {}

  record Facets(List<CategoryCount> categories, List<MoodCount> moods) {}

  /**
   * 목록·검색 조건. {@code excludeSource}는 뺄 출처(예: 소리 검색 API는 Freesound API로 받은 소리를 빼야 해서 {@code
   * Freesound}).
   */
  record Filter(String kind, String category, String mood, String q, String excludeSource) {
    Filter(String kind, String category, String mood, String q) {
      this(kind, category, mood, q, null);
    }
  }

  private record Row(long id, Sound sound) {}

  private static final RowMapper<Row> ROW =
      (rs, n) ->
          new Row(
              rs.getLong("id"),
              new Sound(
                  rs.getString("public_id"),
                  rs.getString("kind"),
                  rs.getString("category"),
                  rs.getString("title"),
                  rs.getString("description"),
                  split(rs.getString("moods")),
                  split(rs.getString("places")),
                  rs.getInt("duration_ms"),
                  new Credit(
                      rs.getString("source"),
                      rs.getString("source_url"),
                      rs.getString("original_title"),
                      rs.getString("author"),
                      rs.getString("license"),
                      rs.getString("license_url"))));

  /** 검색어가 없으면 등록 순 커서 목록, 있으면 가까운 순 한 페이지(다음 커서 없음). */
  CursorPage<Sound> list(Filter f, String cursor, Integer size) {
    int n = CursorPage.size(size);
    String q = text(f.q());
    if (!q.isEmpty()) {
      return new CursorPage<>(search(f, q, n), null);
    }
    long after = Cursor.decodeOr(cursor, Cursor.OLDEST_FIRST).id();
    List<Row> rows =
        jdbc.sql(
                "select "
                    + COLUMNS
                    + " from background_sounds s where "
                    + WHERE
                    + " and s.id > :after order by s.id limit :n")
            .params(filterParams(f))
            .param("after", after)
            .param("n", n + 1)
            .query(ROW)
            .list();
    return CursorPage.of(
        rows,
        n,
        r -> new Cursor(Cursor.OLDEST_FIRST.at(), r.id()),
        page -> page.stream().map(Row::sound).toList());
  }

  private static final String WHERE =
      "s.active and (:kind = '' or s.kind = :kind) and (:category = '' or s.category ="
          + " :category) and (:mood = '' or (',' || s.moods || ',') like '%,' || :mood || ',%')"
          + " and (:excludeSource = '' or s.source <> :excludeSource)";

  private static Map<String, Object> filterParams(Filter f) {
    return Map.of(
        "kind",
        text(f.kind()),
        "category",
        text(f.category()),
        "mood",
        text(f.mood()),
        "excludeSource",
        text(f.excludeSource()));
  }

  /** 검색어와 가까운 소리 n개. 임베딩 서버나 pgvector가 없으면 글자로 찾는다. */
  List<Sound> search(Filter f, String q, int n) {
    return scored(f, q, n).stream().map(SoundMatcher.Scored::sound).toList();
  }

  /** 검색어와 가까운 소리 n개와 거리(코사인). 글자로 찾으면 거리는 순위다. */
  List<SoundMatcher.Scored> scored(Filter f, String q, int n) {
    Optional<String> schema = schema();
    Optional<float[]> v = schema.isPresent() ? embedder.embed(q) : Optional.empty();
    if (schema.isPresent() && v.isPresent()) {
      String s = Vectors.quote(schema.get());
      String distance =
          "s.description_embedding operator(" + s + ".<=>) cast(:v as " + s + ".vector)";
      return jdbc.sql(
              "select "
                  + COLUMNS
                  + ", "
                  + distance
                  + " as distance from background_sounds s where "
                  + WHERE
                  + " and s.description_embedding is not null order by distance limit :n")
          .params(filterParams(f))
          .param("v", Vectors.literal(v.get()))
          .param("n", n)
          .query(
              (rs, i) ->
                  new SoundMatcher.Scored(ROW.mapRow(rs, i).sound(), rs.getDouble("distance")))
          .list();
    }
    String like = "%" + q.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
    return jdbc
        .sql(
            "select "
                + COLUMNS
                + " from background_sounds s where "
                + WHERE
                + " and (lower(s.title) like :q or lower(s.description) like :q or lower(s.places)"
                + " like :q) order by s.id limit :n")
        .params(filterParams(f))
        .param("q", like)
        .param("n", n)
        .query(ROW)
        .list()
        .stream()
        .map(Row::sound)
        .collect(
            java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toList(), SoundMatcher::byRank));
  }

  /** 분위기 기준 글 임베딩. 처음 짐작할 때 한 번 만든다. */
  private final AtomicReference<List<float[]>> moodAnchors = new AtomicReference<>();

  /**
   * 글(줄거리)과 가장 가까운 분위기({@link StoryMood} 이름). 분위기를 적지 않은 예전 콘티의 배경음악을 고를 때 쓴다. 임베딩을 쓸 수 없으면 빈 값.
   */
  Optional<String> guessMood(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    StoryMood[] moods = StoryMood.values();
    List<float[]> anchors = moodAnchors.get();
    if (anchors == null || anchors.size() != moods.length) {
      anchors = embedder.embedAll(Arrays.stream(moods).map(StoryMood::anchor).toList());
      if (anchors.size() != moods.length) {
        return Optional.empty();
      }
      moodAnchors.set(anchors);
    }
    Optional<float[]> v = embedder.embed(text.strip());
    if (v.isEmpty()) {
      return Optional.empty();
    }
    int best = 0;
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < moods.length; i++) {
      double score = dot(v.get(), anchors.get(i));
      if (score > bestScore) {
        best = i;
        bestScore = score;
      }
    }
    return Optional.of(moods[best].name());
  }

  private static double dot(float[] a, float[] b) {
    double sum = 0;
    for (int i = 0; i < Math.min(a.length, b.length); i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }

  /** 종류·카테고리별 수와 분위기별 수. 둘러보기 필터에 쓴다. */
  Facets facets(String excludeSource) {
    String ex = text(excludeSource);
    List<CategoryCount> categories =
        jdbc.sql(
                "select kind, category, count(*) from background_sounds where active and (:ex = ''"
                    + " or source <> :ex) group by kind, category order by kind, count(*) desc,"
                    + " category")
            .param("ex", ex)
            .query((rs, n) -> new CategoryCount(rs.getString(1), rs.getString(2), rs.getLong(3)))
            .list();
    List<MoodCount> moods =
        jdbc.sql(
                "select m, count(*) from background_sounds, unnest(string_to_array(moods, ',')) m"
                    + " where active and (:ex = '' or source <> :ex) and m <> '' group by m order by"
                    + " count(*) desc, m")
            .param("ex", ex)
            .query((rs, n) -> new MoodCount(rs.getString(1), rs.getLong(2)))
            .list();
    return new Facets(categories, moods);
  }

  /** 상세. 뺄 출처의 소리는 없는 소리처럼 404이고, 비슷한 소리에서도 뺀다. */
  Detail get(String publicId, String excludeSource) {
    String ex = text(excludeSource);
    Row row = find(publicId);
    if (!ex.isEmpty() && ex.equals(row.sound().credit().source())) {
      throw notFound();
    }
    List<Sound> similar =
        schema()
            .map(
                s ->
                    jdbc
                        .sql(
                            "select "
                                + COLUMNS
                                + " from background_sounds s, background_sounds o where o.id ="
                                + " :id and s.active and s.id <> o.id and (:ex = '' or s.source <>"
                                + " :ex) and s.audio_embedding is not"
                                + " null and o.audio_embedding is not null order by"
                                + " s.audio_embedding operator("
                                + Vectors.quote(s)
                                + ".<=>) o.audio_embedding limit "
                                + SIMILAR)
                        .param("id", row.id())
                        .param("ex", ex)
                        .query(ROW)
                        .list()
                        .stream()
                        .map(Row::sound)
                        .toList())
            .orElse(List.of());
    return new Detail(row.sound(), similar);
  }

  /** 미리 듣기 MP3의 사전 서명 GET 주소. 꺼진 소리는 주지 않는다. */
  String audioUrl(String publicId) {
    String key =
        jdbc.sql("select audio_key from background_sounds where public_id = :id and active")
            .param("id", valid(publicId))
            .query(String.class)
            .optional()
            .orElseThrow(SoundLibrary::notFound);
    return storage.presignGet(key, AUDIO_TTL);
  }

  private Row find(String publicId) {
    return jdbc.sql(
            "select " + COLUMNS + " from background_sounds s where s.public_id = :id and s.active")
        .param("id", valid(publicId))
        .query(ROW)
        .optional()
        .orElseThrow(SoundLibrary::notFound);
  }

  private Optional<String> schema() {
    Optional<String> s = vectorSchema.get();
    if (s == null) {
      s =
          jdbc.sql(
                  "select udt_schema from information_schema.columns where table_schema ="
                      + " current_schema() and table_name = 'background_sounds' and column_name ="
                      + " 'description_embedding'")
              .query(String.class)
              .optional();
      vectorSchema.set(s);
    }
    return s;
  }

  private static String valid(String publicId) {
    if (!PublicId.isValid(publicId)) {
      throw notFound();
    }
    return publicId;
  }

  private static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND, "배경음을 찾을 수 없어요.");
  }

  private static List<String> split(String csv) {
    return csv == null || csv.isBlank()
        ? List.of()
        : Arrays.stream(csv.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList();
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }
}
