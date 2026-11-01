package net.java21.fourcut.conversation.scenario;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 장면 후보와 콘티를 pipeline-work에 저장하고 읽는다. */
@Component
public class ScenarioStore {

  /** 저장 형식 {@code {candidates: [...]}}. 대화 API가 그대로 보여 준다. */
  record Candidates(List<SceneCandidate> candidates) {}

  /** 저장 형식 {@code {names: ["민지", "준호"]}}. */
  record RealNameList(List<String> names) {}

  private static final String NAMES = "names.json";

  private final ObjectStorage storage;
  private final JsonMapper json;

  public ScenarioStore(ObjectStorage storage, JsonMapper json) {
    this.storage = storage;
    this.json = json;
  }

  List<SceneCandidate> candidates(String id) {
    return storage
        .get(WorkKeys.candidates(id))
        .map(b -> json.readValue(b, Candidates.class).candidates())
        .orElse(List.of());
  }

  String saveCandidates(String id, List<SceneCandidate> candidates) {
    return put(WorkKeys.candidates(id), new Candidates(candidates));
  }

  /** 장면 추출이 찾은 대화 속 실명. 콘티 입력에서 가명으로 바꾸는 데 쓴다. API에는 내보내지 않는다. */
  List<String> names(String id) {
    return storage
        .get(WorkKeys.work(id, NAMES))
        .map(b -> json.readValue(b, RealNameList.class).names())
        .orElse(List.of());
  }

  void saveNames(String id, List<String> names) {
    put(WorkKeys.work(id, NAMES), new RealNameList(names));
  }

  /** 검수까지 끝난 콘티. comic 모듈이 읽는다. */
  public ComicScript script(String id) {
    byte[] bytes =
        storage.get(WorkKeys.script(id)).orElseThrow(() -> new IllegalStateException("콘티가 없다"));
    return json.readValue(bytes, ComicScript.class);
  }

  String saveScript(String id, ComicScript script) {
    return put(WorkKeys.script(id), script);
  }

  private String put(String key, Object value) {
    storage.put(
        key, json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8), "application/json");
    return key;
  }
}
