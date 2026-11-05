package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.TestcontainersConfiguration;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 캐릭터 등록 → 참조 그림 업로드 → pgvector로 콘티 인물 찾기 (REQ-018). 테스트 DB는 pgvector 이미지다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CharacterDbIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private CharacterLibrary library;
  @Autowired private CharacterVectors vectors;
  @Autowired private ObjectStorage storage;
  @MockitoBean private TextEmbedder embedder;

  /** 앞 두 축만 쓰는 768차원 단위 벡터. */
  private static float[] unit(double x, double y) {
    float[] v = new float[CharacterProperties.DIMENSIONS];
    double n = Math.sqrt(x * x + y * y);
    v[0] = (float) (x / n);
    v[1] = (float) (y / n);
    return v;
  }

  @Test
  @DisplayName("REQ-018: 등록한 캐릭터를 설명 임베딩으로 찾고, 끄면 찾지 않는다")
  void registerAndMatch() throws Exception {
    when(embedder.model()).thenReturn("test-embedding");
    when(embedder.embed(anyString()))
        .thenAnswer(
            inv -> {
              String text = inv.getArgument(0);
              if (text.contains("red")) {
                return Optional.of(unit(1, 0.05));
              }
              if (text.contains("woman")) {
                return Optional.of(unit(1, 0));
              }
              return Optional.of(unit(0, 1));
            });
    assertThat(vectors.available()).isTrue();

    String body =
        mvc.perform(
                post("/internal/characters")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"윤슬\",\"look\":\"young woman, short red bob hair, round"
                            + " glasses, yellow raincoat\",\"description\":\"cheerful woman"
                            + " with glasses\",\"scale\":0.6}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.character.referenceUploaded").value(false))
            .andExpect(jsonPath("$.referenceUploadUrl").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.character.publicId");

    // 참조 그림이 없으면 쓰지 않는다
    List<CharacterLibrary.Wanted> wanted =
        List.of(new CharacterLibrary.Wanted("A", "민지", "girl with red hair"));
    assertThat(library.match(wanted, "DAILY")).isEmpty();

    storage.put(CharacterKeys.reference(id), new byte[] {1, 2, 3}, "image/png");
    CharacterRef ref = library.match(wanted, "DAILY").get("A");
    assertThat(ref.publicId()).isEqualTo(id);
    assertThat(ref.look()).contains("red bob hair");
    assertThat(ref.distance()).isLessThan(0.01);
    assertThat(ref.embedReady()).isFalse();

    // 거리가 멀면 쓰지 않는다
    assertThat(library.match(List.of(new CharacterLibrary.Wanted("A", "민지", "old man")), "DAILY"))
        .isEmpty();

    // 워커가 embedding을 올리면 다음부터 그것을 쓴다
    storage.put(ref.embedTargetKey(), new byte[] {4}, "application/octet-stream");
    library.embedsUploaded(List.of(id));
    assertThat(library.match(wanted, "DAILY").get("A").embedKey()).isEqualTo(ref.embedTargetKey());

    mvc.perform(get("/internal/characters"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.publicId=='" + id + "')].embedReady").value(true))
        .andExpect(
            jsonPath("$[?(@.publicId=='" + id + "')].embeddingModel").value("test-embedding"));

    mvc.perform(
            post("/internal/characters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"윤슬\",\"look\":\"x\"}"))
        .andExpect(status().isConflict());

    mvc.perform(delete("/internal/characters/" + id)).andExpect(status().isNoContent());
    assertThat(library.match(wanted, "DAILY")).isEmpty();
  }
}
