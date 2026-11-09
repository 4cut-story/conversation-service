package net.java21.fourcut.conversation.comic;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.character.CharacterRef;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Concept;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * 로컬 도구: PANEL_JOBS의 looks.json({id: {speaker: DB 외모}})과 id-script.json으로 운영 DrawHandler 프롬프트를 만들어
 * panels.json에 쓴다. 커밋하지 않는다.
 */
class PanelJobsHarness {

  @Test
  @EnabledIfEnvironmentVariable(named = "PANEL_JOBS", matches = ".+")
  void run() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    File dir = new File(System.getenv("PANEL_JOBS"));
    JsonNode looks = json.readTree(new File(dir, "looks.json"));
    ArrayNode out = json.createArrayNode();
    for (var e : looks.properties()) {
      File f = new File(dir, e.getKey() + "-script.json");
      if (!f.exists()) {
        continue;
      }
      ComicScript written = json.readValue(f, ComicScript.class);
      Map<String, CharacterRef> cast = new HashMap<>();
      for (var l : e.getValue().properties()) {
        cast.put(
            l.getKey(),
            new CharacterRef(
                l.getKey(),
                l.getKey(),
                l.getValue().asString(),
                1.0,
                java.util.Set.of("family-09/B", "mystery-08/A", "mystery-08/B")
                        .contains(e.getKey() + "/" + l.getKey())
                    ? null
                    : "ref",
                null,
                null,
                0));
      }
      ComicScript script = DrawHandler.adults(DrawHandler.withLooks(written.withRoleCalls(), cast));
      List<Map<String, Staging.Side>> stages = Staging.panels(script);
      String style = Concept.of(script.concept()).style();
      for (int i = 0; i < script.panels().size(); i++) {
        var split =
            System.getenv("PANEL_SPLIT") == null ? null : DrawHandler.split(script.panels().get(i));
        if (split != null) {
          var p = split.frontPanel();
          var img = out.addObject();
          img.put("name", e.getKey() + "-panel-" + (i + 1));
          img.put("comic", e.getKey());
          img.put("stage", 1);
          img.put("prompt", DrawHandler.prompt(style, script, p, Staging.panel(script, p)));
          img.put("panels", script.panels().size());
          img.putArray("characters")
              .addObject()
              .put("speaker", split.front())
              .put("dbLook", e.getValue().path(split.front()).asString())
              .put("side", "CENTER");
          var add = out.addObject();
          add.put("name", e.getKey() + "-panel-" + (i + 1) + "-b");
          add.put("comic", e.getKey());
          add.put("stage", 2);
          add.put("baseFrom", e.getKey() + "-panel-" + (i + 1));
          add.put("prompt", DrawHandler.addPrompt(style, script, script.panels().get(i), split));
          add.put("panels", script.panels().size());
          add.putArray("characters")
              .addObject()
              .put("speaker", split.back())
              .put("dbLook", e.getValue().path(split.back()).asString())
              .put("side", "RIGHT");
          continue;
        }
        var hold =
            System.getenv("PANEL_HOLD") == null ? null : DrawHandler.hold(script.panels().get(i));
        if (hold != null) {
          var p = hold.emptyPanel();
          var img = out.addObject();
          img.put("name", e.getKey() + "-panel-" + (i + 1));
          img.put("comic", e.getKey());
          img.put("stage", 1);
          img.put("prompt", DrawHandler.prompt(style, script, p, stages.get(i)));
          img.put("panels", script.panels().size());
          var refs = img.putArray("characters");
          for (var st : stages.get(i).entrySet()) {
            refs.addObject()
                .put("speaker", st.getKey())
                .put("dbLook", e.getValue().path(st.getKey()).asString())
                .put("side", st.getValue().name());
          }
          var add = out.addObject();
          add.put("name", e.getKey() + "-panel-" + (i + 1) + "-b");
          add.put("comic", e.getKey());
          add.put("stage", 2);
          add.put("baseFrom", e.getKey() + "-panel-" + (i + 1));
          add.put("prompt", DrawHandler.holdPrompt(style, hold));
          add.put("panels", script.panels().size());
          add.putArray("characters");
          continue;
        }
        var img = out.addObject();
        img.put("name", e.getKey() + "-panel-" + (i + 1));
        img.put("comic", e.getKey());
        img.put("prompt", DrawHandler.prompt(style, script, script.panels().get(i), stages.get(i)));
        img.put("panels", script.panels().size());
        var refs = img.putArray("characters");
        for (var st : stages.get(i).entrySet()) {
          refs.addObject()
              .put("speaker", st.getKey())
              .put("dbLook", e.getValue().path(st.getKey()).asString())
              .put("side", st.getValue().name());
        }
      }
      json.writeValue(new File(dir, e.getKey() + "-drawn-script.json"), script);
    }
    json.writeValue(new File(dir, "panels.json"), out);
    System.out.println("panels " + out.size());
  }
}
