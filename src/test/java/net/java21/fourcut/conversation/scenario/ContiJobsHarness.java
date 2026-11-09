package net.java21.fourcut.conversation.scenario;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 로컬 도구: CONTI_JOBS의 jobs.json(id, concept, dialect, src)마다 새 콘티 지시·틀로 바꾼 LLM 작업 파일을 쓴다. 커밋하지 않는다.
 */
class ContiJobsHarness {

  @Test
  @EnabledIfEnvironmentVariable(named = "CONTI_JOBS", matches = ".+")
  void run() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    File dir = new File(System.getenv("CONTI_JOBS"));
    for (var j : json.readTree(new File(dir, "jobs.json"))) {
      ObjectNode job = (ObjectNode) json.readTree(new File(j.path("src").asString()));
      job.put(
          "system",
          Prompts.scriptSystem(
                  Concept.of(j.path("concept").asString()),
                  Dialect.of(j.path("dialect").asString()))
              .strip());
      job.set("jsonSchema", json.readTree(Prompts.SCRIPT_SCHEMA));
      job.remove("imageFiles");
      json.writeValue(new File(dir, j.path("id").asString() + "-SCRIPT.json"), job);
    }
  }
}
