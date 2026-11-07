package net.java21.fourcut.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 테스트 전용 수동 LLM 처리: 표시한 대화의 LLM 작업은 워커가 가져가지 않고, 주인이 서버가 만든 프롬프트를 꺼내 답을 넣는다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManualLlmFlowTest {

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ContentClient content;

  private final HttpClient http = HttpClient.newHttpClient();

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from conversations");
    jdbc.update("delete from gpu_workers");
  }

  @Test
  @DisplayName("수동 처리 대화의 장면 고르기 작업은 워커가 가져가지 않고, 주인이 프롬프트를 꺼내 답을 넣으면 끝난다")
  void manualExtract() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, true);
    upload(owner, id);
    finish(owner, id);
    String transcribe = claim();
    result(transcribe, PipelineFlowIntegrationTest.transcript());

    // LLM 작업은 대기열에 있지만 워커에게 나가지 않는다
    mvc.perform(
            worker(post("/internal/gpu-jobs/claim"))
                .content("{\"types\":[\"TRANSCRIBE\",\"LLM\",\"IMAGE\"],\"version\":\"0.1.0\"}"))
        .andExpect(status().isNoContent());

    String jobs =
        mvc.perform(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat((List<?>) JsonPath.read(jobs, "$")).hasSize(1);
    assertThat((String) JsonPath.read(jobs, "$[0].step")).isEqualTo("EXTRACT");
    assertThat((String) JsonPath.read(jobs, "$[0].payload.prompt")).contains("내 접시");
    Integer jobId = JsonPath.read(jobs, "$[0].jobId");

    // 다른 사람은 꺼내지도 답하지도 못한다
    mvc.perform(as(get("/api/v1/conversations/{id}/llm-jobs", id), PublicId.generate()))
        .andExpect(status().isNotFound());

    ObjectNode answer = json.createObjectNode();
    answer.put("content", json.writeValueAsString(PipelineFlowIntegrationTest.extracted()));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/llm-jobs/{jobId}/result", id, jobId), owner)
                .content(json.writeValueAsString(answer)))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "select state from gpu_jobs where id = ?", String.class, jobId.longValue()))
        .isEqualTo("SUCCEEDED");

    // 이미 답한 작업에는 다시 넣을 수 없다
    mvc.perform(
            as(post("/api/v1/conversations/{id}/llm-jobs/{jobId}/result", id, jobId), owner)
                .content(json.writeValueAsString(answer)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("FR-007: 수동 처리 대화의 합성 단계는 말풍선 자리 확인을 LLM 작업으로 넣을 수 있다")
  void composeJobAllowed() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, true);
    Long conversation =
        jdbc.queryForObject("select id from conversations where public_id = ?", Long.class, id);
    jdbc.update(
        "insert into gpu_jobs (conversation_id, step, type, payload, manual)"
            + " values (?, 'COMPOSE', 'LLM', '{}'::json, true)",
        conversation);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from gpu_jobs where conversation_id = ? and step = 'COMPOSE'",
                Integer.class,
                conversation))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("수동 처리 표시가 없는 대화는 꺼낼 작업이 없고 답을 넣을 수 없다")
  void notManual() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, false);
    mvc.perform(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner))
        .andExpect(status().isOk())
        .andExpect(r -> assertThat(r.getResponse().getContentAsString()).isEqualTo("[]"));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/llm-jobs/{jobId}/result", id, 1), owner)
                .content("{\"content\":\"{}\"}"))
        .andExpect(status().isNotFound());
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String user) {
    return r.header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder worker(MockHttpServletRequestBuilder r) {
    return r.header("X-Worker-Id", "gpu-pc-1").contentType(MediaType.APPLICATION_JSON);
  }

  private String create(String owner, boolean manual) throws Exception {
    return JsonPath.read(
        mvc.perform(
                as(post("/api/v1/conversations"), owner)
                    .content(
                        "{\"source\":\"UPLOAD\",\"consent\":true,\"manualLlm\":" + manual + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  private void upload(String owner, String id) throws Exception {
    String url =
        JsonPath.read(
            mvc.perform(
                    as(post("/api/v1/conversations/{id}/chunks", id), owner)
                        .content("{\"index\":0,\"contentType\":\"audio/webm\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.putUrl");
    HttpResponse<Void> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(new byte[] {1, 2, 3}))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private void finish(String owner, String id) throws Exception {
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":1}"))
        .andExpect(status().isAccepted());
  }

  private String claim() throws Exception {
    return mvc.perform(
            worker(post("/internal/gpu-jobs/claim"))
                .content("{\"types\":[\"TRANSCRIBE\",\"LLM\",\"IMAGE\"],\"version\":\"0.1.0\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private void result(String claimed, Object result) throws Exception {
    ObjectNode request = json.createObjectNode();
    request.put("leaseToken", (String) JsonPath.read(claimed, "$.leaseToken"));
    request.set("result", json.valueToTree(result));
    request.putObject("modelInfo").put("name", "fake");
    request.put("durationMs", 100);
    mvc.perform(
            worker(
                    post(
                        "/internal/gpu-jobs/{jobId}/result",
                        (String) JsonPath.read(claimed, "$.jobId")))
                .content(json.writeValueAsString(request)))
        .andExpect(status().isOk());
  }
}
