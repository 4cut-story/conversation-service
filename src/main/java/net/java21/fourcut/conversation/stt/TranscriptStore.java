package net.java21.fourcut.conversation.stt;

import java.nio.charset.StandardCharsets;
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

  public String save(String conversationId, Transcript transcript) {
    String key = WorkKeys.transcript(conversationId);
    storage.put(
        key,
        json.writeValueAsString(transcript).getBytes(StandardCharsets.UTF_8),
        "application/json");
    return key;
  }

  public Transcript load(String conversationId) {
    byte[] bytes =
        storage
            .get(WorkKeys.transcript(conversationId))
            .orElseThrow(() -> new IllegalStateException("전사 결과가 없다"));
    return json.readValue(bytes, Transcript.class);
  }

  /** 교정 전 원본 전사. 아직 교정하지 않았으면 지금 전사다. */
  public Transcript loadOriginal(String conversationId) {
    return storage
        .get(WorkKeys.rawTranscript(conversationId))
        .map(bytes -> json.readValue(bytes, Transcript.class))
        .orElseGet(() -> load(conversationId));
  }

  /** 교정한 전사로 바꾼다. 처음 바꿀 때 원본을 따로 남긴다. 이후 단계는 {@link #load}로 교정본을 읽는다. */
  public void saveCorrected(String conversationId, Transcript corrected) {
    String raw = WorkKeys.rawTranscript(conversationId);
    if (!storage.exists(raw)) {
      storage.put(
          raw,
          json.writeValueAsString(load(conversationId)).getBytes(StandardCharsets.UTF_8),
          "application/json");
    }
    save(conversationId, corrected);
  }
}
