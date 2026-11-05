package net.java21.fourcut.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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

/** FR-015: 녹음 중 조각이 올라올 때마다 구간 전사를 낮은 우선순위로 넣고, 녹음이 끝나면 미리 전사한 앞부분을 마무리 전사에 넘긴다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreTranscribeFlowTest {

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
  @DisplayName("FR-015: 구간 전사는 보통 작업 뒤에 가져가고, 결과는 다음 구간과 마무리 전사로 이어진다")
  void preTranscribes() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, "RECORDING");

    upload(owner, id, 0);
    assertThat(parts(id)).isZero();
    upload(owner, id, 1); // 조각 0이 다 올라왔다 → 구간 전사 1건
    upload(owner, id, 2); // 진행 중인 구간이 있으면 더 넣지 않는다
    assertThat(parts(id)).isEqualTo(1);

    // 다른 대화의 보통 작업이 나중에 들어와도 먼저 나간다
    String other = create(owner, "UPLOAD");
    upload(owner, other, 0);
    finish(owner, other, 1);
    String first = claim();
    assertThat(stepOf(first)).isEqualTo("TRANSCRIBE");
    assertThat(conversationOf(first)).isEqualTo(other);

    String part = claim();
    assertThat(stepOf(part)).isEqualTo("TRANSCRIBE_PART");
    assertThat((String) JsonPath.read(part, "$.input.mode")).isEqualTo("part");
    assertThat((List<?>) JsonPath.read(part, "$.input.audioUrls")).hasSize(1);
    assertThat((Double) JsonPath.read(part, "$.input.fromSec")).isZero();
    Map<String, Object> word = Map.of("word", "가", "start", 1.0, "end", 1.5);
    result(
        part,
        Map.of(
            "segments",
            List.of(Map.of("start", 1.0, "end", 1.5, "text", "가", "words", List.of(word))),
            "cut",
            50.0,
            "language",
            "ko",
            "skipped",
            false));

    // 다음 조각을 물으면 앞 경계(50초)부터 이어서 구간 전사를 넣는다
    upload(owner, id, 3);
    String nextPayload =
        jdbc.queryForObject(
            "select payload::text from gpu_jobs j join conversations c on c.id = j.conversation_id"
                + " where c.public_id = ? and j.step = 'TRANSCRIBE_PART' and j.state = 'QUEUED'",
            String.class,
            id);
    assertThat((Double) JsonPath.read(nextPayload, "$.fromSec")).isEqualTo(50.0);
    assertThat((Integer) JsonPath.read(nextPayload, "$.chunks")).isEqualTo(3);

    // 녹음이 끝나면 아직 안 가져간 구간은 취소되고, 마무리 전사가 미리 전사한 앞부분을 받는다
    finish(owner, id, 4);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from gpu_jobs j join conversations c on c.id = j.conversation_id"
                    + " where c.public_id = ? and j.error_code = 'CANCELLED'",
                Integer.class,
                id))
        .isEqualTo(1);
    String last = claim();
    assertThat(stepOf(last)).isEqualTo("TRANSCRIBE");
    assertThat(conversationOf(last)).isEqualTo(id);
    String prefix = get(JsonPath.read(last, "$.input.prefixUrl"));
    assertThat((Double) JsonPath.read(prefix, "$.cut")).isEqualTo(50.0);
    assertThat((String) JsonPath.read(prefix, "$.segments[0].text")).isEqualTo("가");
  }

  @Test
  @DisplayName("FR-015: 오래된 구간 결과나 건너뛴 결과는 앞부분에 넣지 않는다")
  void ignoresSkippedPart() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, "RECORDING");
    upload(owner, id, 0);
    upload(owner, id, 1);
    String part = claim();
    result(part, Map.of("segments", List.of(), "cut", 0.0, "skipped", true));

    upload(owner, id, 2); // 건너뛰었으니 다시 조각 0부터
    String payload =
        jdbc.queryForObject(
            "select payload::text from gpu_jobs j join conversations c on c.id = j.conversation_id"
                + " where c.public_id = ? and j.state = 'QUEUED'",
            String.class,
            id);
    assertThat((Double) JsonPath.read(payload, "$.fromSec")).isZero();
    assertThat((Integer) JsonPath.read(payload, "$.chunks")).isEqualTo(2);

    finish(owner, id, 3);
    String last = claim();
    assertThat(JsonPath.<Map<String, Object>>read(last, "$.input")).doesNotContainKey("prefixUrl");
  }

  private int parts(String id) {
    return jdbc.queryForObject(
        "select count(*) from gpu_jobs j join conversations c on c.id = j.conversation_id"
            + " where c.public_id = ? and j.step = 'TRANSCRIBE_PART'",
        Integer.class,
        id);
  }

  private String stepOf(String claimed) {
    return jdbc.queryForObject(
        "select step from gpu_jobs where id = ?",
        String.class,
        Long.valueOf(JsonPath.read(claimed, "$.jobId")));
  }

  private String conversationOf(String claimed) {
    return jdbc.queryForObject(
        "select c.public_id from gpu_jobs j join conversations c on c.id = j.conversation_id"
            + " where j.id = ?",
        String.class,
        Long.valueOf(JsonPath.read(claimed, "$.jobId")));
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String user) {
    return r.header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder worker(MockHttpServletRequestBuilder r) {
    return r.header("X-Worker-Id", "gpu-pc-1").contentType(MediaType.APPLICATION_JSON);
  }

  private String create(String owner, String source) throws Exception {
    return JsonPath.read(
        mvc.perform(
                as(post("/api/v1/conversations"), owner)
                    .content("{\"source\":\"" + source + "\",\"consent\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(),
        "$.id");
  }

  private void upload(String owner, String id, int index) throws Exception {
    String url =
        JsonPath.read(
            mvc.perform(
                    as(post("/api/v1/conversations/{id}/chunks", id), owner)
                        .content("{\"index\":" + index + ",\"contentType\":\"audio/webm\"}"))
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

  private void finish(String owner, String id, int chunks) throws Exception {
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner)
                .content("{\"chunkCount\":" + chunks + "}"))
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

  private String get(String url) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }
}
