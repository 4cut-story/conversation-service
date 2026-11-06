package net.java21.fourcut.conversation.pipeline;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 외부 LLM 답 캐시. 같은 입력(시스템 지시, 프롬프트, 스키마, 시드, 그림)에는 처음 받은 답을 다시 쓴다. 외부 LLM은 시드를 받지 않아 같은 녹음이라도 매번 다른
 * 장면·콘티가 나왔고, 그래서 고정 시험 세트로 수정 전후를 짝 비교할 수 없었다. 다시 시도는 시드가 달라 캐시를 타지 않는다. 키에 API 모델 이름을 넣어 모델을 바꾸면
 * 새로 묻는다. 캐시를 읽거나 쓰지 못해도 호출은 그대로 한다.
 */
class LlmCache {

  static final String PREFIX = "llm-cache/";

  private static final Logger log = LoggerFactory.getLogger(LlmCache.class);

  private final ObjectStorage storage;
  private final JsonMapper json;
  private final String models;

  /**
   * @param storage null이면 캐시를 쓰지 않는다
   * @param models 키에 넣는 API 모델 이름들
   */
  LlmCache(ObjectStorage storage, JsonMapper json, String models) {
    this.storage = storage;
    this.json = json;
    this.models = models;
  }

  static LlmCache off(JsonMapper json) {
    return new LlmCache(null, json, "");
  }

  Optional<JsonNode> get(JsonNode job) {
    if (storage == null) {
      return Optional.empty();
    }
    try {
      return storage.get(key(job)).map(b -> json.readTree(new String(b, StandardCharsets.UTF_8)));
    } catch (RuntimeException e) {
      log.warn("LLM 캐시 읽기 실패: {}", e.toString());
      return Optional.empty();
    }
  }

  void put(JsonNode job, JsonNode result) {
    if (storage == null) {
      return;
    }
    try {
      storage.put(
          key(job),
          json.writeValueAsString(result).getBytes(StandardCharsets.UTF_8),
          "application/json");
    } catch (RuntimeException e) {
      log.warn("LLM 캐시 쓰기 실패: {}", e.toString());
    }
  }

  String key(JsonNode job) {
    return PREFIX + sha256(models + "\n" + json.writeValueAsString(job)) + ".json";
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
