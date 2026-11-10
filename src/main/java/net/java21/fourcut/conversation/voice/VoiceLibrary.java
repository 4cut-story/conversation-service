package net.java21.fourcut.conversation.voice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.java21.fourcut.conversation.character.TextEmbedder;
import net.java21.fourcut.conversation.support.Vectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 목소리 목록과 찾기. 서버가 뜰 때 {@link #RESOURCE}를 voices 표에 맞추고(없어진 목소리는 끈다), 검색 글이 바뀌었거나 임베딩이 없는 목소리는
 * 한국어·영어 검색 글을 각각 BGE-M3로 임베딩해 채운다. 찾기는 성별·나이대·사투리로 거른 뒤 질의와 두 임베딩 중 더 가까운 쪽의 거리 순이다. 맞는 목소리가 없으면
 * 사투리, 나이대, 성별 순으로 조건을 풀고, 임베딩을 쓸 수 없으면 같은 조건에서 등록 순으로 준다.
 */
@Service
public class VoiceLibrary {

  private static final Logger log = LoggerFactory.getLogger(VoiceLibrary.class);

  /** 목소리 목록 원본. web의 lib/voices-data.json에서 들을 거리(samples)와 만들 때 쓴 문구를 뺀 것이다. */
  static final String RESOURCE = "voices/voices.json";

  /** 찾기마다 받는 후보 수. 이미 다른 인물에 쓴 목소리를 건너뛰려고 넉넉히 받는다. */
  static final int CANDIDATES = 10;

  /** 이 차이 안의 거리는 같은 점수로 본다(배경음 찾기와 같은 값). */
  static final double TIE_MARGIN = 0.02;

  /** 임베딩 요청 한 번에 넣는 목소리 수(검색 글은 두 배). */
  static final int EMBED_BATCH = 4;

  /** 목록 한 줄 (voices.json). */
  record Entry(
      String id,
      String name,
      String gender,
      String ageGroup,
      String dialect,
      String tone,
      List<String> traits,
      String description,
      String searchText,
      String searchTextEn,
      List<String> roles,
      List<String> concepts,
      Double f0Hz,
      Double rate) {}

  /** 찾은 목소리. distance는 설명 임베딩 코사인 거리이고, 임베딩 없이 찾았으면 null이다. */
  public record Voice(
      String id,
      String name,
      String gender,
      String ageGroup,
      String dialect,
      String tone,
      Double distance) {}

  /**
   * 찾을 인물. description은 콘티 인물 설명(영어나 한국어), gender는 FEMALE·MALE, ageGroup은 CHILD·YOUNG·MIDDLE·OLD,
   * dialect는 대화의 사투리 코드(없으면 표준어). 모르는 값은 null.
   */
  public record Query(
      String description, String gender, String ageGroup, String dialect, String concept) {

    public Query(String description, String gender, String ageGroup, String dialect) {
      this(description, gender, ageGroup, dialect, null);
    }
  }

  /** concept이 {@link #ANY_CONCEPT}이면 컨셉을 보지 않는다. */
  private record Filter(String gender, String ageGroup, String dialect, String concept) {}

  private static final String ANY_CONCEPT = "*";

  private static final String COLUMNS = "voice_id, name, gender, age_group, dialect, tone";

  private static final String WHERE =
      "active and (:g = '' or gender = :g) and (:a = '' or age_group = :a) and"
          + " coalesce(dialect, '') = :d and (:c = '*' or coalesce(concepts, '') = '' or concepts"
          + " like '%DAILY%' or (:c <> '' and concepts like '%' || :c || '%'))";

  private final JdbcClient jdbc;
  private final TextEmbedder embedder;
  private final JsonMapper json;
  private final boolean enabled;

  /** vector 타입의 스키마. 컬럼이 없으면(pgvector 없음) 빈 값. 처음 확인한 값을 계속 쓴다. */
  private final AtomicReference<Optional<String>> vectorSchema = new AtomicReference<>();

  VoiceLibrary(
      JdbcClient jdbc,
      TextEmbedder embedder,
      JsonMapper json,
      @Value("${voices.enabled:true}") boolean enabled) {
    this.jdbc = jdbc;
    this.embedder = embedder;
    this.json = json;
    this.enabled = enabled;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    if (enabled) {
      Thread.ofVirtual().name("voice-seed").start(this::seedAndIndex);
    }
  }

  private void seedAndIndex() {
    try {
      int n = seed(load());
      int done = reindex();
      log.info("목소리 {}개를 맞추고 설명 임베딩 {}개를 채웠다", n, done);
    } catch (RuntimeException e) {
      log.warn("목소리 목록을 맞추지 못했다: {}", e.toString());
    }
  }

  /** 임베딩 서버가 늦게 켜졌거나 실패했으면 빠진 임베딩을 다시 채운다. */
  @Scheduled(
      fixedDelayString = "${voices.embed-interval:10m}",
      initialDelayString = "${voices.embed-initial-delay:10m}")
  public void fillMissingEmbeddings() {
    if (!enabled) {
      return;
    }
    int done = reindex();
    if (done > 0) {
      log.info("목소리 설명 임베딩 {}개를 채웠다", done);
    }
  }

  List<Entry> load() {
    try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
      return List.of(json.readValue(in.readAllBytes(), Entry[].class));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * 목록을 표에 맞춘다: 새 목소리는 넣고, 있는 목소리는 고치고, 목록에 없는 목소리는 끈다. 검색 글이 바뀐 목소리는 임베딩 모델을 지워 다시 임베딩하게 한다. 맞춘 수.
   */
  int seed(List<Entry> entries) {
    for (Entry e : entries) {
      jdbc.sql(
              "insert into voices (voice_id, name, gender, age_group, dialect, tone, traits,"
                  + " description, search_text, search_text_en, roles, concepts, f0_hz, rate)"
                  + " values (:id, :name, :g, :a, :d, :tone, :traits, :desc, :ko, :en, :roles,"
                  + " :concepts, :f0, :rate) on conflict (voice_id) do update set name ="
                  + " excluded.name, gender = excluded.gender, age_group = excluded.age_group,"
                  + " dialect = excluded.dialect, tone = excluded.tone, traits = excluded.traits,"
                  + " description = excluded.description, search_text = excluded.search_text,"
                  + " search_text_en = excluded.search_text_en, roles = excluded.roles, concepts ="
                  + " excluded.concepts, f0_hz = excluded.f0_hz, rate = excluded.rate,"
                  + " embedding_model = case when voices.search_text = excluded.search_text and"
                  + " voices.search_text_en = excluded.search_text_en then voices.embedding_model"
                  + " end, active = true, updated_at = current_timestamp")
          .param("id", e.id())
          .param("name", e.name())
          .param("g", code(e.gender()))
          .param("a", code(e.ageGroup()))
          .param("d", blank(e.dialect()) ? null : code(e.dialect()))
          .param("tone", text(e.tone()))
          .param("traits", join(e.traits()))
          .param("desc", text(e.description()))
          .param("ko", text(e.searchText()))
          .param("en", text(e.searchTextEn()))
          .param("roles", join(e.roles()))
          .param("concepts", join(e.concepts()))
          .param("f0", e.f0Hz())
          .param("rate", e.rate())
          .update();
    }
    List<String> ids = entries.stream().map(Entry::id).toList();
    jdbc.sql(
            "update voices set active = false, updated_at = current_timestamp where active and"
                + " not (voice_id = any(:ids))")
        .param("ids", ids.toArray(String[]::new))
        .update();
    return entries.size();
  }

  /** 임베딩이 없거나 다른 모델로 만든 활성 목소리의 한국어·영어 검색 글을 임베딩한다. 임베딩 서버가 응답하지 않으면 0. 채운 목소리 수. */
  int reindex() {
    Optional<String> schema = schema();
    if (schema.isEmpty()) {
      return 0;
    }
    record Todo(String id, String ko, String en) {}
    List<Todo> todo =
        jdbc.sql(
                "select voice_id, search_text, search_text_en from voices where active and"
                    + " (ko_embedding is null or en_embedding is null or embedding_model is"
                    + " distinct from :m) order by id")
            .param("m", embedder.model())
            .query((rs, n) -> new Todo(rs.getString(1), rs.getString(2), rs.getString(3)))
            .list();
    String s = Vectors.quote(schema.get());
    int done = 0;
    // 한 번에 모두 보내면 임베딩 서버가 읽기 제한 시간(10초) 안에 답하지 못해 하나도 채우지 못한다. 몇 개씩 나눠 보내고, 실패한 묶음은 다음 주기에 다시 한다
    for (int from = 0; from < todo.size(); from += EMBED_BATCH) {
      List<Todo> batch = todo.subList(from, Math.min(from + EMBED_BATCH, todo.size()));
      List<String> texts = new ArrayList<>();
      batch.forEach(
          t -> {
            texts.add(t.ko());
            texts.add(t.en());
          });
      List<float[]> v = embedder.embedAll(texts);
      if (v.size() != texts.size()) {
        continue;
      }
      for (int i = 0; i < batch.size(); i++) {
        jdbc.sql(
                "update voices set ko_embedding = cast(:ko as "
                    + s
                    + ".vector), en_embedding = cast(:en as "
                    + s
                    + ".vector), embedding_model = :m, updated_at = current_timestamp where"
                    + " voice_id = :id")
            .param("ko", Vectors.literal(v.get(2 * i)))
            .param("en", Vectors.literal(v.get(2 * i + 1)))
            .param("m", embedder.model())
            .param("id", batch.get(i).id())
            .update();
      }
      done += batch.size();
    }
    return done;
  }

  /**
   * 인물에 가장 가까운 목소리. taken(이미 다른 인물에 쓴 목소리 ID)은 다른 후보가 있으면 건너뛴다. 맞는 조건이 없으면 사투리를 표준어로, 나이대를 아무 나이로,
   * 성별을 아무 성별로 차례로 푼다. 목소리가 하나도 없으면 빈 값.
   */
  public Optional<Voice> match(Query q, Set<String> taken) {
    return match(q, taken, 0);
  }

  /**
   * {@link #match(Query, Set)}와 같고, 거리가 사실상 같은 후보({@link #TIE_MARGIN} 안)가 여럿이면 seed로 그중 하나를 고른다.
   * 임베딩 없이 찾았으면 조건에 맞는 후보가 모두 동점이다. seed가 같으면 결과도 같아 한 만화를 다시 만들어도 같은 목소리가 나온다.
   */
  public Optional<Voice> match(Query q, Set<String> taken, long seed) {
    String gender = code(q.gender());
    String age = code(q.ageGroup());
    String dialect =
        blank(q.dialect()) || "SEOUL".equals(code(q.dialect())) ? "" : code(q.dialect());
    // 사극 목소리처럼 일상(DAILY)에 안 맞는 목소리는 그 컨셉의 만화에만 준다. 아무도 없으면 마지막에 컨셉을 풀어 준다
    String concept = blank(q.concept()) ? "" : code(q.concept());
    List<Filter> tries = new ArrayList<>();
    tries.add(new Filter(gender, age, dialect, concept));
    if (!dialect.isEmpty()) {
      tries.add(new Filter(gender, age, "", concept));
    }
    tries.add(new Filter(gender, "", dialect, concept));
    tries.add(new Filter(gender, "", "", concept));
    tries.add(new Filter("", "", "", concept));
    tries.add(new Filter("", "", "", ANY_CONCEPT));
    Optional<float[]> v = Optional.empty();
    Optional<String> schema = schema();
    if (schema.isPresent() && !blank(q.description())) {
      v = embedder.embed(q.description().strip());
    }
    for (Filter f : tries) {
      List<Voice> found = candidates(f, schema, v);
      if (!found.isEmpty()) {
        List<Voice> free = found.stream().filter(x -> !taken.contains(x.id())).toList();
        return Optional.of(pick(free.isEmpty() ? found : free, seed));
      }
    }
    return Optional.empty();
  }

  /** 첫 후보와 거리 차이가 {@link #TIE_MARGIN} 안인 후보들(거리가 없으면 모두) 중 seed로 하나. */
  static Voice pick(List<Voice> ranked, long seed) {
    Voice best = ranked.get(0);
    List<Voice> tied =
        ranked.stream()
            .filter(
                x ->
                    best.distance() == null
                        || x.distance() != null && x.distance() - best.distance() <= TIE_MARGIN)
            .toList();
    return tied.get((int) Math.floorMod(seed, (long) tied.size()));
  }

  private List<Voice> candidates(Filter f, Optional<String> schema, Optional<float[]> v) {
    if (schema.isPresent() && v.isPresent()) {
      String s = Vectors.quote(schema.get());
      String query = "cast(:v as " + s + ".vector)";
      List<Voice> found =
          jdbc.sql(
                  "select "
                      + COLUMNS
                      + ", least(ko_embedding operator("
                      + s
                      + ".<=>) "
                      + query
                      + ", en_embedding operator("
                      + s
                      + ".<=>) "
                      + query
                      + ") as distance from voices where "
                      + WHERE
                      + " and ko_embedding is not null and en_embedding is not null order by"
                      + " distance, id limit :n")
              .param("g", f.gender())
              .param("a", f.ageGroup())
              .param("d", f.dialect())
              .param("c", f.concept())
              .param("v", Vectors.literal(v.get()))
              .param("n", CANDIDATES)
              .query(
                  (rs, n) -> {
                    Voice x = ROW.mapRow(rs, n);
                    return new Voice(
                        x.id(),
                        x.name(),
                        x.gender(),
                        x.ageGroup(),
                        x.dialect(),
                        x.tone(),
                        rs.getDouble("distance"));
                  })
              .list();
      if (!found.isEmpty()) {
        return found;
      }
    }
    return jdbc.sql("select " + COLUMNS + " from voices where " + WHERE + " order by id limit :n")
        .param("g", f.gender())
        .param("a", f.ageGroup())
        .param("d", f.dialect())
        .param("c", f.concept())
        .param("n", CANDIDATES)
        .query(ROW)
        .list();
  }

  private static final RowMapper<Voice> ROW =
      (rs, n) ->
          new Voice(
              rs.getString("voice_id"),
              rs.getString("name"),
              rs.getString("gender"),
              rs.getString("age_group"),
              rs.getString("dialect"),
              rs.getString("tone"),
              null);

  private Optional<String> schema() {
    Optional<String> s = vectorSchema.get();
    if (s == null) {
      s =
          jdbc.sql(
                  "select udt_schema from information_schema.columns where table_schema ="
                      + " current_schema() and table_name = 'voices' and column_name ="
                      + " 'ko_embedding'")
              .query(String.class)
              .optional();
      vectorSchema.set(s);
    }
    return s;
  }

  static String code(String value) {
    return text(value).toUpperCase(Locale.ROOT);
  }

  static String join(List<String> values) {
    return values == null
        ? ""
        : String.join(
            ",", values.stream().map(VoiceLibrary::text).filter(x -> !x.isEmpty()).toList());
  }

  static List<String> split(String csv) {
    return blank(csv)
        ? List.of()
        : Arrays.stream(csv.split(",")).map(String::strip).filter(x -> !x.isEmpty()).toList();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }
}
