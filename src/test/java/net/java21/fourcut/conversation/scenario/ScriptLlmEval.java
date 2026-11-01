package net.java21.fourcut.conversation.scenario;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.java21.fourcut.conversation.stt.Transcript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 실제 Ollama(qwen3)로 콘티 프롬프트를 돌려 서버 검증을 통과하는지 본다. CI에서는 돌지 않는다.
 *
 * <p>{@code OLLAMA_EVAL_URL=http://localhost:11434 ./mvnw test -Dtest=ScriptLlmEval}
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_EVAL_URL", matches = ".+")
class ScriptLlmEval {

  private final JsonMapper json = JsonMapper.builder().build();

  static Transcript transcript() {
    String[][] rows = {
      {"A", "0", "어 얘들아 냉장고에 있던 내 치킨 누가 먹었어?"},
      {"B", "4", "치킨? 무슨 치킨?"},
      {"A", "7", "어제 반 남겨 둔 거 있잖아. 양념 반."},
      {"C", "11", "음 나는 오늘 하루 종일 밖에 있었는데"},
      {"B", "14", "나도 몰라. 진짜 몰라."},
      {"A", "18", "근데 B 너 입가에 양념 묻어 있는데?"},
      {"B", "22", "아 이거 떡볶이야 떡볶이"},
      {"C", "25", "ㅋㅋㅋㅋ 떡볶이를 언제 먹었는데"},
      {"B", "28", "아까... 아까 먹었어"},
      {"A", "31", "쓰레기통에 치킨 상자 있던데 그것도 떡볶이 상자야?"},
      {"B", "36", "알았어 알았어 내가 먹었어. 미안해."},
      {"C", "40", "ㅋㅋㅋ 결국 자백했네"},
      {"B", "43", "대신 오늘 저녁 내가 치킨 쏠게"},
      {"A", "47", "오 진짜? 그럼 두 마리 시켜"},
      {"C", "50", "나도 끼워 줘 나도"},
      {"B", "53", "아니 왜 너까지"},
    };
    List<Transcript.Utterance> us = new ArrayList<>();
    for (String[] r : rows) {
      double t = Double.parseDouble(r[1]);
      us.add(new Transcript.Utterance(r[0], t, t + 3, r[2]));
    }
    return new Transcript("ko", List.of("A", "B", "C"), us);
  }

  /** 실명이 나오고 화자가 둘인 대화. */
  static Transcript transcript2() {
    String[][] rows = {
      {"A", "0", "지수야 너 어제 단톡방에 이상한 사진 올렸더라"},
      {"B", "4", "아 그거 강아지 사진인데"},
      {"A", "7", "강아지가 아니라 너 셀카였어 민준이가 캡처했어"},
      {"B", "11", "헐 진짜? 바로 지워야겠다"},
      {"A", "14", "이미 늦었어 민준이가 프로필 사진으로 바꿨어"},
      {"B", "18", "아 민준이 진짜 가만 안 둬"},
      {"A", "21", "근데 사진 잘 나왔던데 ㅋㅋ"},
      {"B", "24", "그치? 사실 나도 마음에 들었어"},
      {"A", "28", "그럼 그냥 둬 ㅋㅋㅋ"},
      {"B", "31", "아니 그래도 민준이 프사는 안 돼"},
    };
    List<Transcript.Utterance> us = new ArrayList<>();
    for (String[] r : rows) {
      double t = Double.parseDouble(r[1]);
      us.add(new Transcript.Utterance(r[0], t, t + 3, r[2]));
    }
    return new Transcript("ko", List.of("A", "B"), us);
  }

  @Test
  @DisplayName("qwen3 콘티 출력이 서버 검증을 통과하는지 여러 시드로 확인한다")
  void scripts() throws Exception {
    String url = System.getenv("OLLAMA_EVAL_URL");
    int runs = Integer.parseInt(System.getenv().getOrDefault("OLLAMA_EVAL_RUNS", "3"));
    boolean second = "2".equals(System.getenv("OLLAMA_EVAL_CASE"));
    Transcript transcript = second ? transcript2() : transcript();
    SceneCandidate scene =
        second
            ? new SceneCandidate(
                "FUNNY", 0, 34, "B의 셀카가 단톡방에 올라가 친구 프사가 된다", "당황하다 결국 마음에 들어하는 반전", 0.8, true)
            : new SceneCandidate(
                "FUNNY", 0, 56, "B가 A의 치킨을 몰래 먹고 떡볶이라고 우기다 들킨다", "발뺌하다 들통나는 반전", 0.9, true);
    String prompt = ScriptHandler.prompt(transcript, scene);
    HttpClient http = HttpClient.newHttpClient();
    int ok = 0;
    for (int seed = 1; seed <= runs; seed++) {
      ObjectNode body = json.createObjectNode();
      body.put("model", System.getenv().getOrDefault("OLLAMA_EVAL_MODEL", "qwen3:8b"));
      var messages = body.putArray("messages");
      messages.addObject().put("role", "system").put("content", Prompts.SCRIPT_SYSTEM.strip());
      messages.addObject().put("role", "user").put("content", prompt);
      body.set("format", json.readTree(Prompts.SCRIPT_SCHEMA));
      ObjectNode options = body.putObject("options");
      options.put("num_ctx", 16384);
      options.put("temperature", 0.4);
      options.put("seed", seed);
      options.put("num_predict", Prompts.SCRIPT_MAX_TOKENS);
      body.put("stream", false);
      body.put("think", false);
      HttpResponse<String> res =
          http.send(
              HttpRequest.newBuilder(URI.create(url + "/api/chat"))
                  .timeout(Duration.ofMinutes(20))
                  .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode data = json.readTree(res.body());
      String content = data.path("message").path("content").asString("");
      System.out.println(
          "=== seed "
              + seed
              + " eval_count="
              + data.path("eval_count")
              + " done_reason="
              + data.path("done_reason"));
      System.out.println(content);
      String verdict;
      try {
        int from = content.indexOf('{');
        int to = content.lastIndexOf('}');
        ComicScript script =
            json.treeToValue(json.readTree(content.substring(from, to + 1)), ComicScript.class)
                .normalized();
        script = script.withFictionalNames(ScriptHandler.spoken(transcript));
        verdict = script.violation();
        if (verdict == null) {
          List<String> clash = script.namesFoundIn(ScriptHandler.spoken(transcript));
          verdict = clash.isEmpty() ? null : "name clash " + clash;
        }
      } catch (RuntimeException e) {
        verdict = "파싱 실패: " + e.getMessage();
      }
      System.out.println("--- verdict: " + (verdict == null ? "OK" : verdict));
      if (verdict == null) {
        ok++;
      }
    }
    System.out.println("PASSED " + ok + "/" + runs);
  }
}
