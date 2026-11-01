package net.java21.fourcut.conversation.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import net.java21.fourcut.conversation.comic.ComicProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * model-runtime eval/comic_eval.py가 쓰는 프롬프트 묶음을 내보낸다. 서버와 같은 프롬프트로 GPU PC에서 반복 실행하기 위해서다.
 *
 * <p>{@code PROMPT_BUNDLE_OUT=../model-runtime/eval/prompt_bundle.json ./mvnw test
 * -Dtest=PromptBundleExport}
 */
@EnabledIfEnvironmentVariable(named = "PROMPT_BUNDLE_OUT", matches = ".+")
class PromptBundleExport {

  @Test
  @DisplayName("서버 프롬프트·스키마·그림 설정을 JSON으로 내보낸다")
  void export() throws Exception {
    JsonMapper json = JsonMapper.builder().build();
    ObjectNode b = json.createObjectNode();
    b.put("promptVersion", Prompts.VERSION);
    b.put("windowSec", 600);
    b.put("contextSec", 60);
    b.put("numCtx", 16384);
    b.put("temperature", 0.4);
    ObjectNode extract = b.putObject("extract");
    extract.put("system", Prompts.EXTRACT_SYSTEM.strip());
    extract.set("schema", json.readTree(Prompts.EXTRACT_SCHEMA));
    extract.put("maxTokens", Prompts.EXTRACT_MAX_TOKENS);
    ObjectNode script = b.putObject("script");
    script.put("system", Prompts.SCRIPT_SYSTEM.strip());
    script.set("schema", json.readTree(Prompts.SCRIPT_SCHEMA));
    script.put("maxTokens", Prompts.SCRIPT_MAX_TOKENS);
    ObjectNode draw = b.putObject("draw");
    draw.put("style", ComicProperties.DEFAULT_STYLE);
    draw.put("negativePrompt", ComicProperties.DEFAULT_NEGATIVE);
    draw.put("imageSize", ComicProperties.DEFAULT_IMAGE_SIZE);
    var concepts = b.putArray("concepts");
    for (Concept c : Concept.values()) {
      ObjectNode n = concepts.addObject();
      n.put("id", c.name());
      n.put("label", c.label());
      n.put("style", c.style());
      n.put("negative", c.negative());
      n.put("scriptSystem", Prompts.scriptSystem(c).strip());
    }
    var spare = b.putArray("castNames");
    CastNames.POOL.forEach(spare::add);
    Path out = Path.of(System.getenv("PROMPT_BUNDLE_OUT"));
    Files.writeString(out, json.writerWithDefaultPrettyPrinter().writeValueAsString(b) + "\n");
  }
}
