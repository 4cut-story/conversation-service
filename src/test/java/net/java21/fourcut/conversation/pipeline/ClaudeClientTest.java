package net.java21.fourcut.conversation.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ClaudeClientTest {

  private static final String URL = "https://claude.test/v1/messages";

  private final JsonMapper json = JsonMapper.builder().build();
  private final ClaudeProperties properties =
      new ClaudeProperties(
          "test-key",
          "claude-x",
          "low",
          4000,
          "https://claude.test",
          Duration.ofSeconds(5),
          Duration.ofMinutes(10));
  private MockRestServiceServer server;
  private ClaudeClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-api-key", properties.apiKey())
            .defaultHeader("anthropic-version", ClaudeClient.API_VERSION);
    server = MockRestServiceServer.bindTo(builder).build();
    client = new ClaudeClient(builder.build(), properties, json);
  }

  private JsonNode job() {
    return json.readTree(
        """
        {"model":"qwen3:8b","system":"장면을 고른다","prompt":"전사",
         "jsonSchema":{"type":"object","required":["panels"],"properties":{
           "panels":{"type":"array","minItems":4,"maxItems":4,"items":{"type":"object",
             "properties":{"score":{"type":"integer","minimum":1,"maximum":5}}}}}},
         "options":{"temperature":0.4,"seed":7,"num_predict":900}}
        """);
  }

  @Test
  @DisplayName("LLM 작업을 Claude Messages 요청(구조화 출력)으로 보내고, 텍스트만 워커 결과 모양으로 돌려준다")
  void chat() {
    server
        .expect(requestTo(URL))
        .andExpect(header("x-api-key", "test-key"))
        .andExpect(header("anthropic-version", ClaudeClient.API_VERSION))
        .andExpect(jsonPath("$.model").value("claude-x"))
        .andExpect(jsonPath("$.max_tokens").value(4000))
        .andExpect(jsonPath("$.system").value("장면을 고른다"))
        .andExpect(jsonPath("$.messages[0].role").value("user"))
        .andExpect(jsonPath("$.messages[0].content").value("전사"))
        .andExpect(jsonPath("$.temperature").doesNotExist())
        .andExpect(jsonPath("$.output_config.effort").value("low"))
        .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
        .andExpect(jsonPath("$.output_config.format.schema.additionalProperties").value(false))
        .andRespond(
            withSuccess(
                """
                {"model":"claude-x","stop_reason":"end_turn","content":[
                  {"type":"thinking","thinking":""},{"type":"text","text":"{\\"panels\\":[]}"}],
                 "usage":{"output_tokens":12}}
                """,
                MediaType.APPLICATION_JSON));

    JsonNode result = client.chat(job());

    assertThat(result.path("content").asString()).isEqualTo("{\"panels\":[]}");
    assertThat(result.path("model").asString()).isEqualTo("claude-x");
    assertThat(result.path("evalCount").asInt()).isEqualTo(12);
    assertThat(client.name()).isEqualTo("claude");
    assertThat(client.model()).isEqualTo("claude-x");
    assertThat(client.timeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(client.cooldown()).isEqualTo(Duration.ofMinutes(10));
    server.verify();
  }

  @Test
  @DisplayName("구조화 출력이 받지 않는 제약은 지우고 설명에 남기며, 모든 객체를 닫는다")
  void schema() {
    JsonNode schema = client.schema(job().path("jsonSchema"));

    assertThat(schema.path("additionalProperties").asBoolean(true)).isFalse();
    JsonNode panels = schema.path("properties").path("panels");
    assertThat(panels.has("minItems")).isFalse();
    assertThat(panels.has("maxItems")).isFalse();
    assertThat(panels.path("description").asString()).contains("minItems=4", "maxItems=4");
    JsonNode item = panels.path("items");
    assertThat(item.path("additionalProperties").asBoolean(true)).isFalse();
    JsonNode score = item.path("properties").path("score");
    assertThat(score.has("minimum")).isFalse();
    assertThat(score.path("description").asString()).contains("minimum=1", "maximum=5");

    JsonNode kept =
        client.schema(
            json.readTree(
                "{\"type\":\"array\",\"minItems\":1,\"description\":\"줄\",\"maxLength\":3,"
                    + "\"items\":{\"type\":\"string\",\"enum\":[\"a\"]}}"));
    assertThat(kept.path("minItems").asInt()).isEqualTo(1);
    assertThat(kept.path("description").asString()).isEqualTo("줄 (maxLength=3)");
    assertThat(kept.path("items").path("enum").path(0).asString()).isEqualTo("a");
    assertThat(kept.has("additionalProperties")).isFalse();
  }

  @Test
  @DisplayName("스키마와 시스템 지시가 없으면 형식 없이 보낸다")
  void plainRequest() {
    JsonNode body = client.request(json.readTree("{\"prompt\":\"p\"}"));
    assertThat(body.has("system")).isFalse();
    assertThat(body.path("output_config").has("format")).isFalse();
  }

  @Test
  @DisplayName("키 만료(401)·권한 없음(403)·한도 초과(429)·과부하(529)는 쉬어야 하는 실패로 알린다")
  void unavailable() {
    for (int status : new int[] {401, 403, 429, 529}) {
      server.reset();
      server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(status)));
      assertThatThrownBy(() -> client.chat(job()))
          .isInstanceOf(LlmClient.Unavailable.class)
          .hasMessageContaining(String.valueOf(status));
    }
  }

  @Test
  @DisplayName("서버 오류, 거절·잘린 응답, 빈 응답은 실패로 본다")
  void failures() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> client.chat(job()))
        .isInstanceOf(IllegalStateException.class)
        .isNotInstanceOf(LlmClient.Unavailable.class);

    assertThatThrownBy(() -> client.result(json.readTree("{\"stop_reason\":\"refusal\"}")))
        .hasMessageContaining("refusal");
    assertThatThrownBy(
            () ->
                client.result(
                    json.readTree(
                        "{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"{\"}]}")))
        .hasMessageContaining("max_tokens");
    assertThatThrownBy(() -> client.result(json.readTree("{\"content\":[]}")))
        .hasMessageContaining("비었다");
  }

  @Test
  @DisplayName("키가 비어 있으면 Claude를 쓰지 않는다")
  void enabled() {
    assertThat(properties.enabled()).isTrue();
    assertThat(new ClaudeProperties("", "m", "low", 1, "u", Duration.ZERO, Duration.ZERO).enabled())
        .isFalse();
    assertThat(
            new ClaudeProperties(null, "m", "low", 1, "u", Duration.ZERO, Duration.ZERO).enabled())
        .isFalse();
    assertThat(ClaudeClient.create(RestClient.builder(), properties, json)).isNotNull();
  }
}
