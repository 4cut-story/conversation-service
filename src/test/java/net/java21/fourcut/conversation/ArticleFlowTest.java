package net.java21.fourcut.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.pipeline.Production;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 기사로 만들기: 기사 URL을 넣으면 서버가 본문을 읽어 바로 처리를 시작하고, 전사 단계가 기사를 대화로 각색하는 LLM 작업이 된다. 각색한 대화가 전사로 저장되면 장면
 * 고르기부터는 녹음과 같다. 테스트는 수동 LLM 처리로 각색 답을 직접 넣는다.
 */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "article.allow-private=true",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ArticleFlowTest {

  static final String PARAGRAPH = "동네 시장 한가운데에 30년 된 떡집이 문을 닫는다는 소식이 전해지자 단골손님들이 아침부터 줄을 섰다.";

  private static HttpServer server;
  private static String base;

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectStorage storage;
  @Autowired private Production production;

  @MockitoBean private ContentClient content;

  @BeforeAll
  static void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/news/1",
        ex -> {
          String html =
              """
              <html lang="ko"><head><meta property="og:title" content="30년 떡집의 마지막 날">
              <meta property="og:site_name" content="예시신문"></head><body>
              <nav>정치 경제 사회</nav><article>
              <p>%s</p>
              <p>주인 할머니는 그동안 고마웠다며 남은 떡을 모두 나눠 주었고, 손님들은 꽃다발과 손편지로 답했다.</p>
              <p>가게 자리에는 손주가 카페를 열 예정이라 단골들은 다시 오겠다고 약속했다.</p>
              <p>이웃 상인들은 떡집 앞에 작은 현수막을 걸어 30년 동안 시장을 지켜 준 할머니에게 고마운 마음을 전했고, 할머니는 눈시울을 붉혔다.</p>
              <p>할머니는 마지막 손님을 배웅한 뒤 가게 문에 "그동안 정말 감사했습니다"라는 쪽지를 붙이고 불을 껐다.</p>
              </article></body></html>
              """
                  .formatted(PARAGRAPH);
          byte[] body = html.getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.createContext(
        "/paid",
        ex -> {
          byte[] body =
              "<html><body><article><p>구독자 전용 기사예요.</p></article></body></html>"
                  .getBytes(StandardCharsets.UTF_8);
          ex.getResponseHeaders().add("Content-Type", "text/html");
          ex.sendResponseHeaders(200, body.length);
          ex.getResponseBody().write(body);
          ex.close();
        });
    server.start();
    base = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from conversations");
  }

  @Test
  @DisplayName("기사로 만들기: URL을 넣으면 본문을 읽어 대화로 각색하고, 장면 고르기로 넘어간다")
  void articleToDialogue() throws Exception {
    String owner = PublicId.generate();
    String created =
        mvc.perform(
                as(post("/api/v1/conversations"), owner)
                    .content(
                        "{\"source\":\"ARTICLE\",\"url\":\""
                            + base
                            + "/news/1\",\"manualLlm\":true,\"concept\":\"NEWSPAPER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.source").value("ARTICLE"))
        .andExpect(jsonPath("$.status").value("TRANSCRIBING"))
        .andExpect(jsonPath("$.sourceUrl").value(base + "/news/1"))
        .andExpect(jsonPath("$.sourceTitle").value("30년 떡집의 마지막 날"))
        .andExpect(jsonPath("$.language").value("ko"));
    assertThat(storage.exists(WorkKeys.article(id))).isTrue();

    String jobs = body(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner));
    assertThat((List<?>) JsonPath.read(jobs, "$")).hasSize(1);
    assertThat((String) JsonPath.read(jobs, "$[0].step")).isEqualTo("TRANSCRIBE");
    assertThat((String) JsonPath.read(jobs, "$[0].payload.prompt"))
        .contains("떡집")
        .doesNotContain("정치");
    Integer jobId = JsonPath.read(jobs, "$[0].jobId");

    answer(owner, id, jobId, dialogue());

    String transcript =
        new String(storage.get(WorkKeys.transcript(id)).orElseThrow(), StandardCharsets.UTF_8);
    assertThat(transcript).contains("할머니, 진짜 문 닫으세요?");
    String next = body(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner));
    assertThat((String) JsonPath.read(next, "$[0].step")).isEqualTo("EXTRACT");
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.status").value("SELECTING"));

    Long internal =
        jdbc.queryForObject("select id from conversations where public_id = ?", Long.class, id);
    ObjectNode made = production.of(internal, false).orElseThrow().production();
    assertThat(made.path("source").path("type").asString()).isEqualTo("ARTICLE");
    assertThat(made.path("source").path("url").asString()).isEqualTo(base + "/news/1");
    assertThat(made.path("source").path("site").asString()).isEqualTo("예시신문");
  }

  @Test
  @DisplayName("기사로 만들기: 각색 결과가 기사 문장을 그대로 옮기면 다시 각색한다")
  void copiedDialogueRetries() throws Exception {
    String owner = PublicId.generate();
    String id = create(owner, base + "/news/1");
    String jobs = body(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner));
    Integer jobId = JsonPath.read(jobs, "$[0].jobId");
    ObjectNode copied = dialogue();
    ((ArrayNode) copied.path("lines")).addObject().put("speaker", "A").put("text", PARAGRAPH);
    answer(owner, id, jobId, copied);
    String again = body(as(get("/api/v1/conversations/{id}/llm-jobs", id), owner));
    assertThat((String) JsonPath.read(again, "$[0].step")).isEqualTo("TRANSCRIBE");
    assertThat((Integer) JsonPath.read(again, "$[0].jobId")).isNotEqualTo(jobId);
  }

  @Test
  @DisplayName("기사로 만들기: 본문을 못 찾거나, 내부 주소거나, 주소가 없으면 만들지 않고 이유를 알린다")
  void unreadable() throws Exception {
    String owner = PublicId.generate();
    mvc.perform(
            as(post("/api/v1/conversations"), owner)
                .content("{\"source\":\"ARTICLE\",\"url\":\"" + base + "/paid\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.error.code").value("ARTICLE_UNREADABLE"))
        .andExpect(
            jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("유료 기사")));
    mvc.perform(
            as(post("/api/v1/conversations"), owner)
                .content("{\"source\":\"ARTICLE\",\"url\":\"file:///etc/passwd\"}"))
        .andExpect(status().isUnprocessableContent());
    mvc.perform(as(post("/api/v1/conversations"), owner).content("{\"source\":\"ARTICLE\"}"))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("select count(*) from conversations", Integer.class)).isZero();
  }

  private ObjectNode dialogue() {
    ObjectNode content = json.createObjectNode();
    content.put("language", "ko");
    ArrayNode lines = content.putArray("lines");
    String[][] said = {
      {"A", "할머니, 진짜 문 닫으세요?"},
      {"B", "그래, 나 이 시장에서 30년 떡 쪘으면 됐지."},
      {"A", "아침부터 줄이 저 끝까지 섰어요."},
      {"C", "저는 꽃 사 왔어요. 그동안 고마웠다고요."},
      {"B", "아이고, 남은 떡 다 가져가."},
      {"A", "손주가 여기 카페 한다면서요?"},
      {"B", "응, 그러니까 또 놀러 와."}
    };
    for (String[] line : said) {
      lines.addObject().put("speaker", line[0]).put("text", line[1]);
    }
    return content;
  }

  private String create(String owner, String url) throws Exception {
    return JsonPath.read(
        body(
            as(post("/api/v1/conversations"), owner)
                .content("{\"source\":\"ARTICLE\",\"url\":\"" + url + "\",\"manualLlm\":true}")),
        "$.id");
  }

  private void answer(String owner, String id, Integer jobId, ObjectNode content) throws Exception {
    ObjectNode answer = json.createObjectNode();
    answer.put("content", json.writeValueAsString(content));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/llm-jobs/{jobId}/result", id, jobId), owner)
                .content(json.writeValueAsString(answer)))
        .andExpect(status().isNoContent());
  }

  private String body(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse().getContentAsString();
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String user) {
    return r.header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON);
  }
}
