package net.java21.fourcut.conversation.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepHandler;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.stt.Transcript;
import net.java21.fourcut.conversation.stt.TranscriptStore;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** EXTRACT: 전사를 10분 구간으로 나눠 구간마다 LLM으로 장면 후보(0~2개)를 뽑는다. 구간을 하나씩 차례로 작업으로 넣는다. */
@Component
class ExtractHandler implements StepHandler {

  private final TranscriptStore transcripts;
  private final ScenarioStore store;
  private final LlmJobs llm;
  private final ScenarioProperties properties;

  ExtractHandler(
      TranscriptStore transcripts,
      ScenarioStore store,
      LlmJobs llm,
      ScenarioProperties properties) {
    this.transcripts = transcripts;
    this.store = store;
    this.llm = llm;
    this.properties = properties;
  }

  @Override
  public Step step() {
    return Step.EXTRACT;
  }

  @Override
  public StepOutcome start(StepContext context) {
    UUID id = context.conversation().id();
    store.saveCandidates(id, List.of());
    return nextWindow(context, transcripts.load(id), -1);
  }

  @Override
  public StepOutcome onResult(StepContext context, JsonNode result) {
    UUID id = context.conversation().id();
    JsonNode content = llm.content(result);
    List<SceneCandidate> found = new ArrayList<>(store.candidates(id));
    for (JsonNode node : content.path("candidates")) {
      SceneCandidate c = llm.read(node, SceneCandidate.class);
      if (c.valid()) {
        found.add(c);
      }
    }
    store.saveCandidates(id, found);
    int window = context.jobPayload().path("window").asInt();
    return nextWindow(context, transcripts.load(id), window);
  }

  private StepOutcome nextWindow(StepContext context, Transcript transcript, int after) {
    int size = properties.windowSec();
    int last = transcript.durationSec() / size;
    for (int w = after + 1; w <= last; w++) {
      List<Transcript.Utterance> part =
          transcript.between((double) w * size, (double) (w + 1) * size);
      if (!part.isEmpty()) {
        ObjectNode extra = llm.object();
        extra.put("window", w);
        String prompt =
            "대화 언어: "
                + transcript.language()
                + "\n화자: "
                + String.join(", ", transcript.speakers())
                + "\n\n전사 ("
                + (w * size / 60)
                + "분부터):\n"
                + Transcript.lines(part);
        long seed = context.conversation().id().getMostSignificantBits() + w + context.attempt();
        return llm.job(Prompts.EXTRACT_SYSTEM, prompt, Prompts.EXTRACT_SCHEMA, seed, extra);
      }
    }
    return new StepOutcome.Done(WorkKeys.candidates(context.conversation().id()), Step.SELECT);
  }
}
