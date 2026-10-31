package net.java21.fourcut.conversation.stt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 전사 결과를 pipeline-work에 저장하고 읽는다. */
@Component
public class TranscriptStore {

  private final ObjectStorage storage;
  private final JsonMapper json;

  public TranscriptStore(ObjectStorage storage, JsonMapper json) {
    this.storage = storage;
    this.json = json;
  }

  public String save(UUID conversationId, Transcript transcript) {
    String key = WorkKeys.transcript(conversationId);
    storage.put(
        key,
        json.writeValueAsString(transcript).getBytes(StandardCharsets.UTF_8),
        "application/json");
    return key;
  }

  public Transcript load(UUID conversationId) {
    byte[] bytes =
        storage
            .get(WorkKeys.transcript(conversationId))
            .orElseThrow(() -> new IllegalStateException("전사 결과가 없다"));
    return json.readValue(bytes, Transcript.class);
  }
}
