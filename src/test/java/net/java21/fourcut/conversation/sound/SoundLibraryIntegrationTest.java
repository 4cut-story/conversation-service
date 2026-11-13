package net.java21.fourcut.conversation.sound;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Optional;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.character.TextEmbedder;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 배경음 공개 API: 거르기, 커서, 설명 임베딩 검색, 글자 검색, 비슷한 소리, 미리 듣기 주소, 꺼진 소리 숨기기. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SoundLibraryIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcClient jdbc;
  @MockitoBean private TextEmbedder embedder;

  private String wave;
  private String rain;
  private String party;

  /** 한 축만 1인 벡터 (가까움을 손으로 정한다). */
  private static String axis(int dims, int at, double other) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < dims; i++) {
      sb.append(i > 0 ? "," : "").append(i == at ? "1" : i == at + 1 ? String.valueOf(other) : "0");
    }
    return sb.append(']').toString();
  }

  private static float[] axisVector(int dims, int at) {
    float[] v = new float[dims];
    v[at] = 1f;
    return v;
  }

  private String insert(
      String kind, String category, String title, String moods, int axis, boolean active) {
    String id = PublicId.generate();
    jdbc.sql(
            "insert into background_sounds (public_id, kind, category, title, description, moods,"
                + " places, duration_ms, audio_key, source, source_id, source_url, original_title,"
                + " author, license, license_url, active, audio_embedding, description_embedding)"
                + " values (:id, :kind, :cat, :title, :title || ' 설명', :moods, '바닷가,항구', 12000,"
                + " 'sounds/' || :id || '/audio.mp3', 'FSD50K', :id, 'https://freesound.org/s/1/',"
                + " 'orig', 'someone', 'CC-BY-3.0', 'http://creativecommons.org/licenses/by/3.0/',"
                + " :active, cast(:a as vector), cast(:d as vector))")
        .param("id", id)
        .param("kind", kind)
        .param("cat", category)
        .param("title", title)
        .param("moods", moods)
        .param("active", active)
        .param("a", axis(512, axis, 0.0))
        .param("d", axis(1024, axis, 0.0))
        .update();
    return id;
  }

  @BeforeEach
  void seed() {
    jdbc.sql("delete from background_sounds").update();
    wave = insert("AMBIENCE", "OCEAN_WAVES", "밤바다 파도", "PEACEFUL,LONELY", 0, true);
    rain = insert("AMBIENCE", "RAIN", "창밖 빗소리", "SAD", 1, true);
    party = insert("MUSIC", "MUSIC_POP", "신나는 파티 음악", "PARTY,EXCITING", 5, true);
    insert("AMBIENCE", "OCEAN_WAVES", "꺼진 파도", "PEACEFUL", 0, false);
  }

  @Test
  @DisplayName("배경음 목록을 종류·카테고리·분위기로 거르고 커서로 넘긴다")
  void browse() throws Exception {
    mvc.perform(get("/api/v1/sounds").param("kind", "AMBIENCE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].title").value("밤바다 파도"))
        .andExpect(jsonPath("$.items[0].moods[1]").value("LONELY"))
        .andExpect(jsonPath("$.items[0].places[0]").value("바닷가"))
        .andExpect(jsonPath("$.items[0].credit.author").value("someone"))
        .andExpect(jsonPath("$.items[0].credit.license").value("CC-BY-3.0"));
    mvc.perform(get("/api/v1/sounds").param("mood", "PARTY"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(party));
    mvc.perform(get("/api/v1/sounds").param("category", "RAIN"))
        .andExpect(jsonPath("$.items[0].id").value(rain));

    String first =
        mvc.perform(get("/api/v1/sounds").param("size", "2"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cursor = JsonPath.read(first, "$.nextCursor");
    mvc.perform(get("/api/v1/sounds").param("cursor", cursor))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());

    mvc.perform(get("/api/v1/sounds").param("kind", "NOISE")).andExpect(status().isBadRequest());

    mvc.perform(get("/api/v1/sounds/facets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[?(@.category=='OCEAN_WAVES')].count").value(1))
        .andExpect(jsonPath("$.moods[?(@.mood=='PARTY')].count").value(1));
  }

  @Test
  @DisplayName("검색어는 설명 임베딩이 가까운 순으로 찾고, 임베딩을 못 받으면 글자로 찾는다")
  void search() throws Exception {
    when(embedder.embed(anyString())).thenReturn(Optional.of(axisVector(1024, 1)));
    mvc.perform(get("/api/v1/sounds").param("q", "비 오는 날"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(rain))
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
    mvc.perform(get("/api/v1/sounds").param("q", "비 오는 날").param("kind", "MUSIC"))
        .andExpect(jsonPath("$.items.length()").value(1));

    when(embedder.embed(anyString())).thenReturn(Optional.empty());
    mvc.perform(get("/api/v1/sounds").param("q", "파티"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(party));
    mvc.perform(get("/api/v1/sounds").param("q", "항구"))
        .andExpect(jsonPath("$.items.length()").value(3));
  }

  @Test
  @DisplayName("상세는 소리 임베딩이 가까운 소리를 붙이고, 미리 듣기 주소를 준다. 꺼진 소리는 숨긴다")
  void detail() throws Exception {
    mvc.perform(get("/api/v1/sounds/" + wave))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sound.title").value("밤바다 파도"))
        .andExpect(jsonPath("$.similar.length()").value(2))
        .andExpect(jsonPath("$.similar[?(@.title=='꺼진 파도')]").isEmpty());
    mvc.perform(get("/api/v1/sounds/" + wave + "/audio"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").isNotEmpty());
    String off =
        jdbc.sql("select public_id from background_sounds where not active")
            .query(String.class)
            .single();
    mvc.perform(get("/api/v1/sounds/" + off)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sounds/" + off + "/audio")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sounds/bad-id")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("소리 검색 API용 excludeSource는 그 출처의 소리를 목록·검색·분류 수·상세·비슷한 소리에서 뺀다")
  void excludeSource() throws Exception {
    jdbc.sql("update background_sounds set source = 'Freesound' where public_id = :id")
        .param("id", rain)
        .update();
    mvc.perform(get("/api/v1/sounds").param("kind", "AMBIENCE").param("excludeSource", "Freesound"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(wave));
    when(embedder.embed(anyString())).thenReturn(Optional.of(axisVector(1024, 1)));
    mvc.perform(get("/api/v1/sounds").param("q", "비").param("excludeSource", "Freesound"))
        .andExpect(jsonPath("$.items[?(@.id=='" + rain + "')]").isEmpty());
    mvc.perform(get("/api/v1/sounds/facets").param("excludeSource", "Freesound"))
        .andExpect(jsonPath("$.categories[?(@.category=='RAIN')]").isEmpty())
        .andExpect(jsonPath("$.moods[?(@.mood=='SAD')]").isEmpty());
    mvc.perform(get("/api/v1/sounds/" + rain).param("excludeSource", "Freesound"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/sounds/" + wave).param("excludeSource", "Freesound"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.similar[?(@.id=='" + rain + "')]").isEmpty());
    mvc.perform(get("/api/v1/sounds/" + rain)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/sounds").param("excludeSource", "Free sound!"))
        .andExpect(status().isBadRequest());
  }
}
