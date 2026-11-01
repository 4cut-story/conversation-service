package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gemini generateContent 호출. 입력은 워커에 넘기는 LLM 작업과 같다(system, prompt, jsonSchema, options). 결과는 워커와
 * 같은 모양 {@code {content, model}}이다. 키는 헤더로만 보내고 로그에 남기지 않는다.
 */
class GeminiClient {

  private final RestClient client;
  private final GeminiProperties properties;
  private final JsonMapper json;

  GeminiClient(RestClient client, GeminiProperties properties, JsonMapper json) {
    this.client = client;
    this.properties = properties;
    this.json = json;
  }

  static GeminiClient create(
      RestClient.Builder builder, GeminiProperties properties, JsonMapper json) {
    RestClient client =
        builder
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-goog-api-key", properties.apiKey())
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        HttpClientSettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(5))
                            .withReadTimeout(properties.timeout())))
            .build();
    return new GeminiClient(client, properties, json);
  }

  /** LLM 작업 하나를 실행한다. 실패하면 예외 (한도 초과는 {@link RateLimited}). */
  JsonNode chat(JsonNode job) {
    ObjectNode body = request(job);
    JsonNode response =
        client
            .post()
            .uri("/models/{model}:generateContent", properties.model())
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(body))
            .exchange(
                (req, res) -> {
                  int status = res.getStatusCode().value();
                  if (status == 429) {
                    throw new RateLimited();
                  }
                  if (status >= 400) {
                    throw new IllegalStateException("Gemini HTTP " + status);
                  }
                  return json.readTree(res.getBody());
                });
    return result(response);
  }

  ObjectNode request(JsonNode job) {
    ObjectNode body = json.createObjectNode();
    body.putObject("systemInstruction")
        .putArray("parts")
        .addObject()
        .put("text", job.path("system").asString(""));
    body.putArray("contents")
        .addObject()
        .put("role", "user")
        .putArray("parts")
        .addObject()
        .put("text", job.path("prompt").asString(""));
    ObjectNode config = body.putObject("generationConfig");
    JsonNode options = job.path("options");
    config.put("temperature", options.path("temperature").asDouble(0.4));
    if (options.has("seed")) {
      config.put("seed", options.path("seed").asLong());
    }
    config.put("responseMimeType", "application/json");
    if (job.path("jsonSchema").isObject()) {
      config.set("responseJsonSchema", job.path("jsonSchema"));
    }
    return body;
  }

  ObjectNode result(JsonNode response) {
    JsonNode candidate = response.path("candidates").path(0);
    String finish = candidate.path("finishReason").asString("");
    if (!finish.isEmpty() && !"STOP".equals(finish)) {
      throw new IllegalStateException("Gemini 응답이 끝나지 않았다: " + finish);
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode part : candidate.path("content").path("parts")) {
      if (!part.path("thought").asBoolean(false)) {
        text.append(part.path("text").asString(""));
      }
    }
    if (text.isEmpty()) {
      throw new IllegalStateException("Gemini 응답이 비었다");
    }
    ObjectNode out = json.createObjectNode();
    out.put("content", text.toString());
    out.put("model", properties.model());
    out.put("evalCount", response.path("usageMetadata").path("candidatesTokenCount").asInt(0));
    return out;
  }

  /** 무료 등급 한도를 넘었다 (HTTP 429). */
  static class RateLimited extends RuntimeException {
    RateLimited() {
      super("Gemini 한도 초과 (429)");
    }
  }
}
