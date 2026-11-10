package net.java21.fourcut.conversation.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.character.TextEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 목소리 목록 맞추기, 한국어·영어 설명 임베딩 채우기, 성별·나이대·사투리로 거른 가까운 목소리 찾기. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "voices.enabled=false",
      "DB_SCHEMA=public"
    })
@Import(TestcontainersConfiguration.class)
class VoiceLibraryIntegrationTest {

  @Autowired private VoiceLibrary library;
  @Autowired private JdbcClient jdbc;
  @MockitoBean private TextEmbedder embedder;

  /** "ko-3"은 3번 축, "en-3"은 103번 축, "q-3"은 3번 축 (가까움을 손으로 정한다). */
  private static float[] vector(String text) {
    float[] v = new float[1024];
    int at = Integer.parseInt(text.replaceAll("\\D", ""));
    v[text.startsWith("en") ? at + 100 : at] = 1f;
    return v;
  }

  private static VoiceLibrary.Entry entry(
      String id, String gender, String age, String dialect, int axis) {
    return new VoiceLibrary.Entry(
        id,
        "이름" + axis,
        gender,
        age,
        dialect,
        "말투",
        List.of("밝음"),
        "설명",
        "ko-" + axis,
        "en-" + axis,
        List.of("역할"),
        List.of("DAILY"),
        200.0,
        5.0);
  }

  private final List<VoiceLibrary.Entry> voices =
      List.of(
          entry("f-young-1", "female", "YOUNG", null, 1),
          entry("f-young-2", "female", "YOUNG", null, 2),
          entry("f-old-1", "female", "OLD", null, 3),
          entry("f-old-gs", "female", "OLD", "GYEONGSANG", 4),
          entry("m-middle-1", "male", "MIDDLE", null, 5));

  @BeforeEach
  void setUp() {
    jdbc.sql("delete from voices").update();
    when(embedder.model()).thenReturn("bge-m3");
    when(embedder.embed(anyString())).thenAnswer(i -> Optional.of(vector(i.getArgument(0))));
    when(embedder.embedAll(anyList()))
        .thenAnswer(
            i ->
                i.<List<String>>getArgument(0).stream()
                    .map(VoiceLibraryIntegrationTest::vector)
                    .toList());
  }

  private int count(String where) {
    return jdbc.sql("select count(*) from voices where " + where).query(Integer.class).single();
  }

  @Test
  @DisplayName("서비스에 넣은 목록을 표에 넣고 모두 켠다")
  void seedsResource() {
    List<VoiceLibrary.Entry> all = library.load();
    assertThat(library.seed(all)).isEqualTo(all.size());
    assertThat(count("active")).isEqualTo(all.size());
    assertThat(count("gender not in ('FEMALE', 'MALE')")).isZero();
  }

  @Test
  @DisplayName("한국어·영어 검색 글을 임베딩하고, 바뀐 글만 다시 임베딩하고, 목록에서 빠진 목소리는 끈다")
  void reindexes() {
    library.seed(voices);
    assertThat(library.reindex()).isEqualTo(5);
    assertThat(count("ko_embedding is not null and en_embedding is not null")).isEqualTo(5);
    assertThat(library.reindex()).isZero();

    List<VoiceLibrary.Entry> changed =
        List.of(voices.get(0), entry("f-young-2", "female", "YOUNG", null, 7));
    library.seed(changed);
    assertThat(count("active")).isEqualTo(2);
    assertThat(library.reindex()).isEqualTo(1);
  }

  @Test
  @DisplayName("성별·나이대로 거른 뒤 설명이 가장 가까운 목소리를 고르고, 이미 쓴 목소리는 건너뛴다")
  void matchesNearest() {
    library.seed(voices);
    library.reindex();

    Optional<VoiceLibrary.Voice> v =
        library.match(new VoiceLibrary.Query("q-2", "FEMALE", "YOUNG", null), Set.of());
    assertThat(v).map(VoiceLibrary.Voice::id).contains("f-young-2");
    assertThat(v.get().distance()).isEqualTo(0.0);

    assertThat(
            library.match(
                new VoiceLibrary.Query("q-2", "FEMALE", "YOUNG", null), Set.of("f-young-2")))
        .map(VoiceLibrary.Voice::id)
        .contains("f-young-1");
    // 가까운 목소리(1번)라도 성별·나이대가 다르면 고르지 않는다
    assertThat(library.match(new VoiceLibrary.Query("q-1", "male", "MIDDLE", null), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("m-middle-1");
  }

  @Test
  @DisplayName("사투리 대화는 그 사투리 목소리를 고르고, 없으면 표준어, 나이대가 없으면 다른 나이대로 푼다")
  void relaxes() {
    library.seed(voices);
    library.reindex();

    assertThat(
            library.match(new VoiceLibrary.Query("q-3", "FEMALE", "OLD", "GYEONGSANG"), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("f-old-gs");
    assertThat(library.match(new VoiceLibrary.Query("q-4", "FEMALE", "OLD", "JEOLLA"), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("f-old-1");
    assertThat(library.match(new VoiceLibrary.Query("q-3", "FEMALE", "OLD", "SEOUL"), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("f-old-1");
    assertThat(library.match(new VoiceLibrary.Query("q-1", "MALE", "CHILD", null), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("m-middle-1");
  }

  @Test
  @DisplayName("설명이 없거나 임베딩이 없으면 같은 조건에서 등록 순으로 고르고, 목소리가 없으면 빈 값이다")
  void fallsBack() {
    assertThat(library.match(new VoiceLibrary.Query("q-1", "FEMALE", "YOUNG", null), Set.of()))
        .isEmpty();
    library.seed(voices);
    assertThat(library.match(new VoiceLibrary.Query("q-2", "FEMALE", "YOUNG", null), Set.of()))
        .map(VoiceLibrary.Voice::id)
        .contains("f-young-1");
    library.reindex();
    Optional<VoiceLibrary.Voice> v =
        library.match(new VoiceLibrary.Query(null, "FEMALE", "OLD", null), Set.of());
    assertThat(v).map(VoiceLibrary.Voice::id).contains("f-old-1");
    assertThat(v.get().distance()).isNull();
  }

  @Test
  @DisplayName("임베딩 서버가 응답하지 않으면 임베딩을 채우지 않는다")
  void embedderDown() {
    when(embedder.embedAll(anyList())).thenReturn(List.of());
    library.seed(voices);
    assertThat(library.reindex()).isZero();
    library.fillMissingEmbeddings();
    assertThat(count("ko_embedding is not null")).isZero();
  }
}
