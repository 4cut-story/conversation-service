package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 캐릭터 등록 → 컨셉별 모습과 참조 그림 업로드 → pgvector로 콘티 인물 찾기 (REQ-018). 테스트 DB는 pgvector 이미지다. */
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
  @DisplayName("REQ-018: 캐릭터의 컨셉별 모습을 설명 임베딩으로 찾고, 끄면 찾지 않는다")
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
                        "{\"name\":\"윤슬\",\"job\":\"의사\",\"gender\":\"FEMALE\","
                            + "\"ageGroup\":\"YOUNG\",\"look\":\"young woman, short red bob hair,"
                            + " round glasses, white doctor coat\",\"scale\":0.6}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.look.concept").value("DAILY"))
            .andExpect(jsonPath("$.look.expression").value("NEUTRAL"))
            .andExpect(jsonPath("$.look.referenceUploaded").value(false))
            .andExpect(jsonPath("$.referenceUploadUrl").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.character.publicId");
    String dailyLook = JsonPath.read(body, "$.look.publicId");

    // 참조 그림이 없으면 쓰지 않는다
    List<CharacterLibrary.Wanted> wanted =
        List.of(new CharacterLibrary.Wanted("A", "민지", "girl with red hair"));
    assertThat(library.match(wanted, "DAILY")).isEmpty();

    storage.put(CharacterKeys.reference(id, dailyLook), new byte[] {1, 2, 3}, "image/png");
    CharacterRef ref = library.match(wanted, "DAILY").get("A");
    assertThat(ref.publicId()).isEqualTo(dailyLook);
    assertThat(ref.look()).contains("red bob hair");
    assertThat(ref.distance()).isLessThan(0.01);
    assertThat(ref.embedReady()).isFalse();

    // 조선 모습이 없으면 조선 만화에서는 쓰지 않고, 붙이면 그 모습을 쓴다
    assertThat(library.match(wanted, "JOSEON")).isEmpty();
    String joseon =
        mvc.perform(
                put("/internal/characters/" + id + "/looks/JOSEON/NEUTRAL")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"look\":\"woman in hanbok, red braided hair, pink chima\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.look.concept").value("JOSEON"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String joseonLook = JsonPath.read(joseon, "$.look.publicId");
    storage.put(CharacterKeys.reference(id, joseonLook), new byte[] {1}, "image/png");
    assertThat(library.match(wanted, "JOSEON").get("A").look()).contains("hanbok");

    // 표정 모습은 찾는 데 쓰지 않는다
    mvc.perform(
            put("/internal/characters/" + id + "/looks/DAILY/SAD")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"look\":\"young woman, short red bob hair, crying\"}"))
        .andExpect(status().isOk());
    assertThat(library.match(wanted, "DAILY").get("A").publicId()).isEqualTo(dailyLook);

    // 거리가 멀면 쓰지 않는다
    assertThat(library.match(List.of(new CharacterLibrary.Wanted("A", "민지", "old man")), "DAILY"))
        .isEmpty();

    // 워커가 embedding을 올리면 다음부터 그것을 쓴다
    storage.put(ref.embedTargetKey(), new byte[] {4}, "application/octet-stream");
    library.embedsUploaded(List.of(dailyLook));
    assertThat(library.match(wanted, "DAILY").get("A").embedKey()).isEqualTo(ref.embedTargetKey());

    mvc.perform(get("/internal/characters"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.publicId=='" + id + "')].lookCount").value(3));
    mvc.perform(get("/internal/characters/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.job").value("의사"))
        .andExpect(jsonPath("$.looks[0].embedReady").value(true))
        .andExpect(jsonPath("$.looks[0].embeddingModel").value("test-embedding"));

    mvc.perform(post("/internal/characters/looks/" + dailyLook + "/reference"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.look.embedReady").value(false));

    mvc.perform(
            post("/internal/characters")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"윤슬\",\"look\":\"x\"}"))
        .andExpect(status().isConflict());

    mvc.perform(delete("/internal/characters/" + id)).andExpect(status().isNoContent());
    assertThat(library.match(wanted, "DAILY")).isEmpty();
  }
}
