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
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GeminiClientTest {

  private static final String URL = "https://gemini.test/v1beta/models/gemini-x:generateContent";

  private final JsonMapper json = JsonMapper.builder().build();
  private final GeminiProperties properties =
      new GeminiProperties(
          "test-key",
          "gemini-x",
          "https://gemini.test/v1beta",
          Duration.ofSeconds(5),
          Duration.ofMinutes(10));
  private MockRestServiceServer server;
  private GeminiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-goog-api-key", properties.apiKey());
    server = MockRestServiceServer.bindTo(builder).build();
    client = new GeminiClient(builder.build(), properties, json);
  }

  private JsonNode job() {
    return json.readTree(
        """
        {"model":"qwen3:8b","system":"장면을 고른다","prompt":"전사","window":0,
         "jsonSchema":{"type":"object","properties":{"candidates":{"type":"array"}}},
         "options":{"num_ctx":16384,"temperature":0.4,"seed":7,"num_predict":900}}
        """);
  }

  @Test
  @DisplayName("LLM 작업을 Gemini 요청으로 바꿔 보내고, 생각이 아닌 텍스트만 워커 결과 모양으로 돌려준다")
  void chat() {
    server
        .expect(requestTo(URL))
        .andExpect(header("x-goog-api-key", "test-key"))
        .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("장면을 고른다"))
        .andExpect(jsonPath("$.contents[0].parts[0].text").value("전사"))
        .andExpect(jsonPath("$.generationConfig.seed").value(7))
        .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
        .andExpect(jsonPath("$.generationConfig.responseJsonSchema.type").value("object"))
        .andRespond(
            withSuccess(
                """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"생각","thought":true},{"text":"{\\"candidates\\":[]}"}]}}],
                 "usageMetadata":{"candidatesTokenCount":12}}
                """,
                MediaType.APPLICATION_JSON));

    JsonNode result = client.chat(job());

    assertThat(result.path("content").asString()).isEqualTo("{\"candidates\":[]}");
    assertThat(result.path("model").asString()).isEqualTo("gemini-x");
    assertThat(result.path("evalCount").asInt()).isEqualTo(12);
    server.verify();
  }

  @Test
  @DisplayName("무료 등급 한도 초과(429)는 RateLimited로 알린다")
  void rateLimited() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    assertThatThrownBy(() -> client.chat(job())).isInstanceOf(GeminiClient.RateLimited.class);
  }

  @Test
  @DisplayName("서버 오류, 끝나지 않은 응답, 빈 응답은 실패로 본다")
  void failures() {
    server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> client.chat(job())).isInstanceOf(IllegalStateException.class);

    JsonNode cut =
        json.readTree(
            "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{\"}]}}]}");
    assertThatThrownBy(() -> client.result(cut)).hasMessageContaining("MAX_TOKENS");
    assertThatThrownBy(() -> client.result(json.readTree("{\"candidates\":[]}")))
        .hasMessageContaining("비었다");
  }

  @Test
  @DisplayName("키가 비어 있으면 Gemini를 쓰지 않는다")
  void enabled() {
    assertThat(properties.enabled()).isTrue();
    assertThat(new GeminiProperties("", "m", "u", Duration.ZERO, Duration.ZERO).enabled())
        .isFalse();
    assertThat(new GeminiProperties(null, "m", "u", Duration.ZERO, Duration.ZERO).enabled())
        .isFalse();
    assertThat(GeminiClient.create(RestClient.builder(), properties, json)).isNotNull();
  }
}
