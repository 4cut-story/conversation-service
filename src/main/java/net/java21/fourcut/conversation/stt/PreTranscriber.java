package net.java21.fourcut.conversation.stt;

import java.nio.charset.StandardCharsets;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.PartTranscriber;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 녹음 중 선행 전사 (FR-015, docs specs/001 research.md 10절). 조각이 올라올 때마다 지금까지의 음성에서 앞 경계 뒤를 전사하고, 결과를
 * {@code pre-transcript.json}에 이어 붙인다. 화자 분리는 녹음이 끝난 뒤 전체 음성으로 한 번 한다.
 *
 * <p>{@code pre-transcript.json}: {@code {chunks, cut, language, segments: [{start, end, text,
 * words}]}}. cut은 미리 전사를 끝낸 지점(초)이고, 그 앞 단어만 segments에 있다. 구간은 한 대화에 한 번에 하나만 돌므로 덮어써도 된다.
 */
@Component
class PreTranscriber implements PartTranscriber {

  private final ObjectStorage storage;
  private final JsonMapper json;

  PreTranscriber(ObjectStorage storage, JsonMapper json) {
    this.storage = storage;
    this.json = json;
  }

  @Override
  public ObjectNode partPayload(ConversationInfo conversation, int uploadedChunks) {
    String id = conversation.publicId();
    ObjectNode state = load(id);
    if (uploadedChunks <= state.path("chunks").asInt(0)) {
      return null;
    }
    ObjectNode payload = json.createObjectNode();
    payload.put("mode", "part");
    payload.put("chunks", uploadedChunks);
    payload.put("fromSec", state.path("cut").asDouble(0));
    ArrayNode keys = payload.putArray("audioGetKeys");
    for (int i = 0; i < uploadedChunks; i++) {
      keys.add(WorkKeys.audioChunk(id, i));
    }
    String language =
        conversation.language() != null
            ? conversation.language()
            : state.path("language").asString(null);
    if (language != null) {
      payload.put("language", language);
    }
    return payload;
  }

  @Override
  public void partDone(ConversationInfo conversation, JsonNode payload, JsonNode result) {
    if (result.path("skipped").asBoolean(false)
        || !result.path("cut").isNumber()
        || !result.path("segments").isArray()) {
      return;
    }
    String id = conversation.publicId();
    ObjectNode state = load(id);
    double from = payload.path("fromSec").asDouble(0);
    double cut = result.path("cut").asDouble();
    // 그 사이 다른 구간이 경계를 옮겼으면(오래된 결과) 버린다
    if (Math.abs(state.path("cut").asDouble(0) - from) > 1e-6 || cut <= from) {
      return;
    }
    ArrayNode segments = (ArrayNode) state.get("segments");
    result.path("segments").forEach(segments::add);
    state.put("cut", cut);
    state.put("chunks", payload.path("chunks").asInt());
    if (!state.hasNonNull("language") && result.path("language").isString()) {
      state.put("language", result.path("language").asString());
    }
    storage.put(
        WorkKeys.preTranscript(id),
        json.writeValueAsString(state).getBytes(StandardCharsets.UTF_8),
        "application/json");
  }

  private ObjectNode load(String id) {
    return storage
        .get(WorkKeys.preTranscript(id))
        .map(bytes -> (ObjectNode) json.readTree(new String(bytes, StandardCharsets.UTF_8)))
        .orElseGet(
            () -> {
              ObjectNode empty = json.createObjectNode();
              empty.put("chunks", 0);
              empty.put("cut", 0.0);
              empty.putNull("language");
              empty.putArray("segments");
              return empty;
            });
  }
}
