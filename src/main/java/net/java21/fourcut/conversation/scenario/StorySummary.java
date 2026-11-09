package net.java21.fourcut.conversation.scenario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 줄거리 요약이 생기기 전에 만든 만화의 요약을 만든다. 새 만화는 콘티를 만들 때 함께 만든다({@link ComicScript#summary()}). 전사와 고른 장면이
 * 남아 있으면(7일) 콘티와 같은 대화(장면과 앞뒤 1분)로, 없으면 콘티의 제목·인물·대사로 외부 LLM에 한 번 묻는다.
 */
@Component
public class StorySummary {

  static final String SYSTEM =
      """
      대화로 만든 기념 만화 아래에 보이는 줄거리 요약을 쓴다. 만화만 봐서는 놓치는 앞뒤 사정까지 받은 대화(또는 콘티)에서 있었던 일
      전체를 3~4문장(250자 이내)으로 쓴다: 어떤 상황에서 시작해 무슨 일이 생겼고 어떻게 끝났는지.
      - 인물은 "인물 호칭"에 적힌 호칭으로만 부른다(예: "손녀가 할머니에게…"). 실명·가명·화자 표시(A, B)는 쓰지 않는다.
      - 대화에 없는 일을 지어 넣지 않고, 대사를 길게 옮기지 않는다.
      - 대화와 같은 언어로, 맞춤법·띄어쓰기를 지켜 평서문(~다)으로 쓴다.
      """;

  static final String SCHEMA =
      """
      {"type":"object","required":["summary"],"properties":{"summary":{"type":"string"}}}
      """;

  private final DirectLlm llm;
  private final ScenarioStore store;
  private final TranscriptStore transcripts;
  private final ObjectStorage storage;
  private final JsonMapper json;

  StorySummary(
      @Lazy DirectLlm llm,
      ScenarioStore store,
      TranscriptStore transcripts,
      ObjectStorage storage,
      JsonMapper json) {
    this.llm = llm;
    this.store = store;
    this.transcripts = transcripts;
    this.storage = storage;
    this.json = json;
  }

  /**
   * 만화의 줄거리 요약. 콘티에 이미 있으면 그것을, 없으면 외부 LLM으로 만든다.
   *
   * @return 콘티가 없거나(지워짐), 외부 LLM을 부를 수 없거나, 답이 비면 빈 값
   */
  public Optional<String> of(String id) {
    if (!storage.exists(WorkKeys.script(id))) {
      return Optional.empty();
    }
    ComicScript script = store.script(id);
    String ready = ComicScript.cleanSummary(script.summary());
    if (ready != null) {
      return Optional.of(ready);
    }
    if (script.characters() == null || script.characters().isEmpty() || !llm.available()) {
      return Optional.empty();
    }
    String prompt = prompt(id, script);
    return llm.chat(DirectLlm.SUMMARY, SYSTEM, prompt, SCHEMA)
        .map(this::summaryOf)
        .map(s -> ComicScript.cleanSummary(toRoles(s, id, script)));
  }

  private String summaryOf(String answer) {
    int from = answer.indexOf('{');
    int to = answer.lastIndexOf('}');
    if (from < 0 || to < from) {
      return null;
    }
    JsonNode node = json.readTree(answer.substring(from, to + 1));
    return node.path("summary").asString(null);
  }

  /** 요약 질문: 인물 호칭과, 남은 대화(장면과 앞뒤 1분) 또는 콘티. */
  String prompt(String id, ComicScript script) {
    StringBuilder roles = new StringBuilder("인물 호칭: ");
    for (ComicScript.Character c : script.characters()) {
      roles.append(c.speaker()).append('=').append(c.callName()).append(", ");
    }
    String head = roles.substring(0, roles.length() - 2) + "\n만화 제목: " + script.title();
    Optional<SceneCandidate> scene = scene(id);
    if (scene.isPresent() && storage.exists(WorkKeys.transcript(id))) {
      Transcript transcript = transcripts.load(id);
      return head
          + "\n\n"
          + ScriptHandler.prompt(
              transcript,
              scene.get(),
              transcript.seed(),
              RealNames.whose(store.names(id), transcript));
    }
    StringBuilder lines = new StringBuilder();
    List<ComicScript.Panel> panels = script.panels() == null ? List.of() : script.panels();
    Map<String, String> calls = script.callNames();
    for (int i = 0; i < panels.size(); i++) {
      for (ComicScript.Line l :
          panels.get(i).lines() == null ? List.<ComicScript.Line>of() : panels.get(i).lines()) {
        lines
            .append(i + 1)
            .append("컷 ")
            .append(calls.getOrDefault(l.speaker(), l.speaker()))
            .append(": ")
            .append(l.text())
            .append('\n');
      }
    }
    return head + "\n\n[만화 대사]\n" + lines;
  }

  /** 대화 속 가명이 요약에 남았으면 호칭으로 바꾼다. */
  private String toRoles(String summary, String id, ComicScript script) {
    if (summary == null || !storage.exists(WorkKeys.transcript(id))) {
      return summary;
    }
    Transcript transcript = transcripts.load(id);
    Map<String, String> pseudonyms = ScriptHandler.cast(transcript, transcript.seed());
    Map<String, String> calls = new HashMap<>();
    for (ComicScript.Character c : script.characters()) {
      String name = pseudonyms.get(c.speaker());
      if (name != null && !name.isBlank()) {
        calls.put(name, c.callName());
      }
    }
    return CastNames.toRoles(summary, calls);
  }

  private Optional<SceneCandidate> scene(String id) {
    return store.candidates(id).stream().filter(SceneCandidate::chosen).findFirst();
  }
}
