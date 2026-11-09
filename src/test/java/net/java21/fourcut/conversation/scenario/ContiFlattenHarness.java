package net.java21.fourcut.conversation.scenario;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로컬 도구: CONTI_FLAT의 jobs.json마다 DIR/id-SCRIPT.answer.json을 콘티로 펼쳐 DIR/id-script.json에 쓴다. 커밋하지
 * 않는다.
 */
class ContiFlattenHarness {

  @Test
  @EnabledIfEnvironmentVariable(named = "CONTI_FLAT", matches = ".+")
  void run() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    File dir = new File(System.getenv("CONTI_FLAT"));
    File jobs = new File(System.getenv().getOrDefault("CONTI_JOBS_FILE", dir + "/jobs.json"));
    for (JsonNode j : json.readTree(jobs)) {
      String id = j.path("id").asString();
      File answer = new File(dir, id + "-SCRIPT.answer.json");
      if (!answer.exists()) {
        continue;
      }
      String content = json.readTree(answer).path("content").asString();
      JsonNode raw =
          json.readTree(content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1));
      ComicScript script =
          json.treeToValue(ScriptDraft.flatten(raw), ComicScript.class)
              .normalized()
              .withConcept(Concept.of(j.path("concept").asString()));
      json.writeValue(new File(dir, id + "-script.json"), script);
      System.out.println("flat " + id + " violation=" + script.violation());
    }
  }
}
