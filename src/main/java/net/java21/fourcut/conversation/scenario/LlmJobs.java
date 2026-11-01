package net.java21.fourcut.conversation.scenario;

import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** LLM 작업 입력 만들기와 결과 풀기. 워커는 받은 그대로 로컬 Ollama에 넘긴다(format = jsonSchema). */
@Component
class LlmJobs {

  private final ScenarioProperties properties;
  private final JsonMapper json;

  LlmJobs(ScenarioProperties properties, JsonMapper json) {
    this.properties = properties;
    this.json = json;
  }

  StepOutcome.Enqueue job(
      String system, String prompt, String schema, int maxTokens, long seed, ObjectNode extra) {
    ObjectNode payload = extra == null ? json.createObjectNode() : extra;
    payload.put("model", properties.llmModel());
    payload.put("system", system.strip());
    payload.put("prompt", prompt);
    payload.set("jsonSchema", json.readTree(schema));
    ObjectNode options = payload.putObject("options");
    options.put("num_ctx", properties.numCtx());
    options.put("temperature", properties.temperature());
    options.put("seed", seed);
    options.put("num_predict", maxTokens);
    ObjectNode info = json.createObjectNode();
    info.put("model", properties.llmModel());
    info.put("promptVersion", Prompts.VERSION);
    info.put("seed", seed);
    return new StepOutcome.Enqueue(JobType.LLM, payload, json.writeValueAsString(info));
  }

  /** 결과 {@code {content: "<JSON 문자열>"}}에서 JSON을 꺼낸다. 앞뒤 군더더기는 버린다. */
  JsonNode content(JsonNode result) {
    String content = result.path("content").asString("");
    int from = content.indexOf('{');
    int to = content.lastIndexOf('}');
    if (from < 0 || to < from) {
      throw new IllegalArgumentException("LLM 결과가 JSON이 아니다");
    }
    return json.readTree(content.substring(from, to + 1));
  }

  <T> T read(JsonNode node, Class<T> type) {
    return json.treeToValue(node, type);
  }

  ObjectNode object() {
    return json.createObjectNode();
  }

  String write(Object value) {
    return json.writeValueAsString(value);
  }
}
