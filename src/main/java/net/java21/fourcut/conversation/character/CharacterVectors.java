package net.java21.fourcut.conversation.character;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 설명 임베딩(pgvector) 읽기·쓰기. 컬럼은 pgvector가 있는 DB에만 있으므로(afterMigrate.sql), 처음 쓸 때 컬럼과 vector 타입의 스키마를
 * 확인하고 없으면 아무것도 하지 않는다. 연산자와 타입은 확장이 설치된 스키마로 한정해 쓴다(검색 경로에 없어도 된다).
 */
@Component
class CharacterVectors {

  private static final Logger log = LoggerFactory.getLogger(CharacterVectors.class);

  /** 가장 가까운 캐릭터 후보 수. 이미 다른 인물에 쓴 캐릭터를 건너뛰려고 몇 명 더 받는다. */
  static final int CANDIDATES = 5;

  private final JdbcClient jdbc;

  /** vector 타입의 스키마. 처음 확인한 값을 계속 쓴다(확장을 새로 만들면 재시작해야 컬럼이 생긴다). */
  private final AtomicReference<Optional<String>> vectorSchema = new AtomicReference<>();

  CharacterVectors(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** 설명 임베딩을 쓸 수 있는지. */
  boolean available() {
    return schema().isPresent();
  }

  private Optional<String> schema() {
    Optional<String> s = vectorSchema.get();
    if (s == null) {
      s =
          jdbc.sql(
                  "select udt_schema from information_schema.columns where table_schema ="
                      + " current_schema() and table_name = 'character_looks' and column_name ="
                      + " 'description_embedding'")
              .query(String.class)
              .optional();
      if (s.isEmpty()) {
        log.warn("pgvector가 없어 캐릭터를 이름으로만 찾는다 (CREATE EXTENSION vector 뒤 재시작하면 켜진다)");
      }
      vectorSchema.set(s);
    }
    return s;
  }

  void save(String lookId, float[] embedding, String model) {
    schema()
        .ifPresent(
            s ->
                jdbc.sql(
                        "update character_looks set description_embedding = cast(:v as "
                            + quote(s)
                            + ".vector), embedding_model = :m, updated_at = current_timestamp"
                            + " where public_id = :id")
                    .param("v", literal(embedding))
                    .param("m", model)
                    .param("id", lookId)
                    .update());
  }

  /** 임베딩이 없거나 다른 모델로 만든, 활성 캐릭터의 기본 표정 모습 공개 ID (오래된 순, 최대 limit개). */
  List<String> missing(String model, int limit) {
    if (!available()) {
      return List.of();
    }
    return jdbc.sql(
            "select l.public_id from character_looks l join characters c on c.id = l.character_id"
                + " where c.active and l.expression = 'NEUTRAL' and (l.description_embedding is"
                + " null or l.embedding_model is distinct from :m) order by l.id limit :n")
        .param("m", model)
        .param("n", limit)
        .query(String.class)
        .list();
  }

  /** 가까운 순 후보: 활성 캐릭터의 그 컨셉 기본 표정 모습. 캐릭터 하나에 모습 하나만 나온다. */
  List<Nearest> nearest(float[] query, String concept) {
    return schema()
        .map(
            s ->
                jdbc.sql(
                        "select l.public_id, l.description_embedding operator("
                            + quote(s)
                            + ".<=>) cast(:v as "
                            + quote(s)
                            + ".vector) as distance from character_looks l join characters c on"
                            + " c.id = l.character_id where c.active and l.expression = 'NEUTRAL'"
                            + " and l.description_embedding is not null and l.concept = :c"
                            + " order by distance limit "
                            + CANDIDATES)
                    .param("v", literal(query))
                    .param("c", concept == null ? "DAILY" : concept)
                    .query((rs, n) -> new Nearest(rs.getString(1), rs.getDouble(2)))
                    .list())
        .orElse(List.of());
  }

  record Nearest(String publicId, double distance) {}

  static String literal(float[] v) {
    StringBuilder sb = new StringBuilder(v.length * 10).append('[');
    for (int i = 0; i < v.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(String.format(Locale.ROOT, "%.7g", v[i]));
    }
    return sb.append(']').toString();
  }

  private static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }
}
