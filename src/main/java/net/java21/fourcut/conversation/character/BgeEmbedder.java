package net.java21.fourcut.conversation.character;

import java.time.Duration;
import java.util.Optional;
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
 * OpenAI 호환 임베딩 서버(기본 BGE-M3, {@code POST /v1/embeddings})로 설명 임베딩을 만든다. 주소
 * (characters.embedding-url)가 비어 있으면 꺼진다. 한국어와 영어 설명이 같은 공간에 놓이고 한도가 없다. 받은 값은 길이 1로 맞춘다.
 */
@Component
class BgeEmbedder implements TextEmbedder {

  private static final Logger log = LoggerFactory.getLogger(BgeEmbedder.class);

  private final RestClient client;
  private final boolean enabled;
  private final String model;
  private final JsonMapper json;

  BgeEmbedder(RestClient.Builder builder, CharacterProperties properties, JsonMapper json) {
    String url = properties.embeddingUrl();
    this.enabled = url != null && !url.isBlank();
    this.model = properties.embeddingModel();
    this.json = json;
    this.client =
        builder
            .baseUrl(enabled ? url : "http://localhost")
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
              .uri("/v1/embeddings")
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
    body.put("model", model);
    body.putArray("input").add(text.strip());
    return body;
  }

  static float[] values(JsonNode response) {
    JsonNode values = response.path("data").path(0).path("embedding");
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
