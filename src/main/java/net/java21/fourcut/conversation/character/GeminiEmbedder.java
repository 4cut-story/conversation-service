package net.java21.fourcut.conversation.character;

import java.time.Duration;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Gemini embedContent로 설명 임베딩을 만든다. 키(GEMINI_API_KEY)가 없으면 꺼진다. 콘티 외모와 캐릭터 설명을 같은 방식으로 비교하므로 양쪽 모두
 * SEMANTIC_SIMILARITY로 받는다. 768차원으로 줄인 값은 길이가 1이 아니라서 여기서 맞춘다.
 */
@Component
class GeminiEmbedder implements TextEmbedder {

  private static final Logger log = LoggerFactory.getLogger(GeminiEmbedder.class);

  private final RestClient client;
  private final boolean enabled;
  private final String model;
  private final JsonMapper json;

  GeminiEmbedder(
      RestClient.Builder builder,
      GeminiProperties gemini,
      CharacterProperties properties,
      JsonMapper json) {
    this.enabled = gemini.enabled();
    this.model = properties.embeddingModel();
    this.json = json;
    this.client =
        builder
            .baseUrl(gemini.baseUrl())
            .defaultHeader("x-goog-api-key", enabled ? gemini.apiKey() : "")
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        HttpClientSettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(3))
                            .withReadTimeout(Duration.ofSeconds(10))))
            .build();
  }

  @Override
  public String model() {
    return model;
  }

  @Override
  public Optional<float[]> embed(String text) {
    if (!enabled || text == null || text.isBlank()) {
      return Optional.empty();
    }
    try {
      String response =
          client
              .post()
              .uri("/models/{model}:embedContent", model)
              .contentType(MediaType.APPLICATION_JSON)
              .body(json.writeValueAsString(request(text)))
              .retrieve()
              .body(String.class);
      return Optional.of(values(json.readTree(response)));
    } catch (RuntimeException e) {
      log.warn("설명 임베딩을 받지 못해 이름으로만 찾는다: {}", e.toString());
      return Optional.empty();
    }
  }

  ObjectNode request(String text) {
    ObjectNode body = json.createObjectNode();
    body.put("model", "models/" + model);
    body.putObject("content").putArray("parts").addObject().put("text", text.strip());
    body.put("taskType", "SEMANTIC_SIMILARITY");
    body.put("outputDimensionality", CharacterProperties.DIMENSIONS);
    return body;
  }

  static float[] values(JsonNode response) {
    JsonNode values = response.path("embedding").path("values");
    if (values.size() != CharacterProperties.DIMENSIONS) {
      throw new IllegalStateException("임베딩 차원이 " + values.size() + "이다");
    }
    float[] v = new float[values.size()];
    double norm = 0;
    for (int i = 0; i < v.length; i++) {
      v[i] = (float) values.get(i).asDouble();
      norm += v[i] * v[i];
    }
    double length = Math.sqrt(norm);
    if (length == 0) {
      throw new IllegalStateException("임베딩이 0이다");
    }
    for (int i = 0; i < v.length; i++) {
      v[i] = (float) (v[i] / length);
    }
    return v;
  }
}
