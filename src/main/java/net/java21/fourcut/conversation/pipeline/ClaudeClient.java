package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Claude Messages API 호출. 작업의 jsonSchema는 구조화 출력(output_config.format)으로 보낸다. 키는 헤더로만 보내고 로그에 남기지
 * 않는다.
 */
class ClaudeClient implements LlmClient {

  static final String API_VERSION = "2023-06-01";

  /** 구조화 출력이 받지 않는 제약. 지우고 설명에 글로 남긴다. */
  private static final List<String> UNSUPPORTED =
      List.of(
          "minimum",
          "maximum",
          "exclusiveMinimum",
          "exclusiveMaximum",
          "multipleOf",
          "minLength",
          "maxLength",
          "maxItems");

  private final RestClient client;
  private final ClaudeProperties properties;
  private final JsonMapper json;

  ClaudeClient(RestClient client, ClaudeProperties properties, JsonMapper json) {
    this.client = client;
    this.properties = properties;
    this.json = json;
  }

  static ClaudeClient create(
      RestClient.Builder builder, ClaudeProperties properties, JsonMapper json) {
    RestClient client =
        builder
            .baseUrl(properties.baseUrl())
            .defaultHeader("x-api-key", properties.apiKey())
            .defaultHeader("anthropic-version", API_VERSION)
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        HttpClientSettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(5))
                            .withReadTimeout(properties.timeout())))
            .build();
    return new ClaudeClient(client, properties, json);
  }

  @Override
  public String name() {
    return "claude";
  }

  @Override
  public String model() {
    return properties.model();
  }

  @Override
  public Duration timeout() {
    return properties.timeout();
  }

  @Override
  public Duration cooldown() {
    return properties.cooldown();
  }

  @Override
  public JsonNode chat(JsonNode job) {
    ObjectNode body = request(job);
    JsonNode response =
        client
            .post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(body))
            .exchange(
                (req, res) -> {
                  int status = res.getStatusCode().value();
                  // 401·403: 키 만료·권한 없음, 429: 한도 초과, 529: 과부하
                  if (status == 401 || status == 403 || status == 429 || status == 529) {
                    throw new Unavailable("Claude HTTP " + status);
                  }
                  if (status >= 400) {
                    throw new IllegalStateException("Claude HTTP " + status);
                  }
                  return json.readTree(res.getBody());
                });
    return result(response);
  }

  ObjectNode request(JsonNode job) {
    ObjectNode body = json.createObjectNode();
    String task = job.path("task").asString(null);
    body.put("model", properties.model(task));
    body.put("max_tokens", properties.maxTokens());
    String system = job.path("system").asString("");
    if (!system.isBlank()) {
      body.put("system", system);
    }
    ObjectNode message = body.putArray("messages").addObject().put("role", "user");
    if (job.path("images").isArray() && !job.path("images").isEmpty()) {
      ArrayNode content = message.putArray("content");
      for (JsonNode image : job.path("images")) {
        content
            .addObject()
            .put("type", "image")
            .putObject("source")
            .put("type", "base64")
            .put("media_type", "image/png")
            .put("data", image.asString(""));
      }
      content.addObject().put("type", "text").put("text", job.path("prompt").asString(""));
    } else {
      message.put("content", job.path("prompt").asString(""));
    }
    ObjectNode output = body.putObject("output_config");
    output.put("effort", properties.effort(task));
    if (job.path("jsonSchema").isObject()) {
      output
          .putObject("format")
          .put("type", "json_schema")
          .set("schema", schema(job.path("jsonSchema")));
    }
    return body;
  }

  /**
   * 구조화 출력용 스키마. 모든 객체에 additionalProperties=false를 두고, 받지 않는 제약(숫자 범위, 길이, 2 이상 minItems 등)은 지운 뒤
   * 설명에 글로 남겨 모델이 따르게 한다.
   */
  JsonNode schema(JsonNode node) {
    if (node.isArray()) {
      ArrayNode out = json.createArrayNode();
      node.forEach(item -> out.add(schema(item)));
      return out;
    }
    if (!node.isObject()) {
      return node;
    }
    ObjectNode out = json.createObjectNode();
    StringBuilder dropped = new StringBuilder();
    for (Map.Entry<String, JsonNode> field : node.properties()) {
      String key = field.getKey();
      JsonNode value = field.getValue();
      boolean drop =
          UNSUPPORTED.contains(key)
              || ("minItems".equals(key) && value.asInt(0) > 1)
              || "additionalProperties".equals(key);
      if (drop) {
        if (!"additionalProperties".equals(key)) {
          dropped.append(dropped.isEmpty() ? "" : ", ").append(key).append('=').append(value);
        }
      } else if ("properties".equals(key) && value.isObject()) {
        ObjectNode props = out.putObject("properties");
        for (Map.Entry<String, JsonNode> prop : value.properties()) {
          props.set(prop.getKey(), schema(prop.getValue()));
        }
      } else {
        out.set(key, schema(value));
      }
    }
    if ("object".equals(node.path("type").asString("")) || node.has("properties")) {
      out.put("additionalProperties", false);
    }
    if (!dropped.isEmpty()) {
      String description = out.path("description").asString("");
      out.put(
          "description", (description.isEmpty() ? "" : description + " ") + "(" + dropped + ")");
    }
    return out;
  }

  ObjectNode result(JsonNode response) {
    String stop = response.path("stop_reason").asString("");
    if (!stop.isEmpty() && !"end_turn".equals(stop)) {
      throw new IllegalStateException("Claude 응답이 끝나지 않았다: " + stop);
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode block : response.path("content")) {
      if ("text".equals(block.path("type").asString(""))) {
        text.append(block.path("text").asString(""));
      }
    }
    if (text.isEmpty()) {
      throw new IllegalStateException("Claude 응답이 비었다");
    }
    ObjectNode out = json.createObjectNode();
    out.put("content", text.toString());
    out.put("model", response.path("model").asString(properties.model()));
    out.put("evalCount", response.path("usage").path("output_tokens").asInt(0));
    return out;
  }
}
