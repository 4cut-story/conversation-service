package net.java21.fourcut.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import net.java21.fourcut.conversation.support.PublicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 녹음 → 처리 시작 → 가짜 GPU 워커가 작업을 하나씩 가져가 결과를 보고 → 합성 → 만화함 등록. 같은 테스트가 REST Docs 스니펫을 만든다. */
@SpringBootTest(
    properties = {
      "pipeline.recovery-interval=1h",
      "pipeline.local-recovery-interval=1h",
      "DB_SCHEMA=public"
    })
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(TestcontainersConfiguration.class)
class PipelineFlowIntegrationTest {

  static final String WORKER = "gpu-pc-1";

  private static final HeaderDescriptor USER =
      headerWithName("X-User-Id").description("게이트웨이가 붙인 사용자 ID");
  private static final HeaderDescriptor WORKER_ID =
      headerWithName("X-Worker-Id").description("워커 이름 (토큰은 게이트웨이가 확인)");

  private static final FieldDescriptor[] VIEW = {
    fieldWithPath("id").description("대화 ID"),
    fieldWithPath("source").description("RECORDING 또는 UPLOAD"),
    fieldWithPath("status")
        .description(
            "보여 줄 상태. RECORDING, QUEUED, TRANSCRIBING, SELECTING, SCRIPTING, DRAWING, COMPOSING, DONE,"
                + " GPU_WAITING(GPU 워커가 꺼져 기다리는 중), FAILED, NO_SCENE"),
    fieldWithPath("stage").description("저장된 진행 단계 (GPU_WAITING일 때 원래 단계)"),
    fieldWithPath("progress").description("대략의 진행률 0~100"),
    fieldWithPath("retries").description("지금까지 단계를 다시 시도한 횟수"),
    fieldWithPath("failureReason")
        .type(JsonFieldType.STRING)
        .optional()
        .description("실패 이유 (사용자에게 보여 줄 문장)"),
    fieldWithPath("comicId")
        .type(JsonFieldType.STRING)
        .optional()
        .description("완성된 만화 ID (content-service 만화함)"),
    fieldWithPath("language")
        .type(JsonFieldType.STRING)
        .optional()
        .description("ko 또는 en. 전사 후 감지값"),
    fieldWithPath("durationSec")
        .type(JsonFieldType.NUMBER)
        .optional()
        .description("대화 길이(초). 전사 후 채워진다"),
    fieldWithPath("concept")
        .description(
            "만화 컨셉. AUTO(장면에 맞게 고름), DAILY, ANIMALS, WATERCOLOR, PICTURE_BOOK, NEWSPAPER, JOSEON, WUXIA"),
    fieldWithPath("createdAt").description("만든 시각")
  };

  @Autowired private MockMvc mvc;
  @Autowired private JsonMapper json;
  @Autowired private ObjectStorage storage;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private ContentClient content;

  private final HttpClient http = HttpClient.newHttpClient();

  @AfterEach
  void cleanUp() {
    jdbc.update("delete from conversations");
    jdbc.update("delete from gpu_workers");
  }

  static RestDocumentationResultHandler doc(String id, Snippet... snippets) {
    return document(
        id, preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), snippets);
  }

  static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r, String user) {
    return r.header("X-User-Id", user).contentType(MediaType.APPLICATION_JSON);
  }

  static MockHttpServletRequestBuilder worker(MockHttpServletRequestBuilder r) {
    return r.header("X-Worker-Id", WORKER).contentType(MediaType.APPLICATION_JSON);
  }

  String body(ResultActions actions) throws Exception {
    return actions.andReturn().getResponse().getContentAsString();
  }

  void put(String url, byte[] bytes) throws Exception {
    HttpResponse<Void> response =
        http.send(
            HttpRequest.newBuilder(URI.create(url))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  /** 녹음 대화를 만들고 조각 2개를 올린 뒤 처리를 시작한다. */
  String startedConversation(String owner) throws Exception {
    String id =
        JsonPath.read(
            body(
                mvc.perform(
                    as(post("/api/v1/conversations"), owner)
                        .content("{\"source\":\"RECORDING\",\"consent\":true}"))),
            "$.id");
    for (int i = 0; i < 2; i++) {
      String url =
          JsonPath.read(
              body(
                  mvc.perform(
                      as(post("/api/v1/conversations/{id}/chunks", id), owner)
                          .content(
                              "{\"index\":" + i + ",\"contentType\":\"audio/webm;codecs=opus\"}"))),
              "$.putUrl");
      put(url, new byte[] {1, 2, 3});
    }
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":2}"))
        .andExpect(status().isAccepted());
    return id;
  }

  String claim() throws Exception {
    return body(
        mvc.perform(
                worker(post("/internal/gpu-jobs/claim"))
                    .content(
                        "{\"types\":[\"TRANSCRIBE\",\"LLM\",\"IMAGE\"],\"version\":\"0.1.0\"}"))
            .andExpect(status().isOk()));
  }

  void result(String claimed, Object result) throws Exception {
    ObjectNode request = json.createObjectNode();
    request.put("leaseToken", (String) JsonPath.read(claimed, "$.leaseToken"));
    request.set("result", json.valueToTree(result));
    request.putObject("modelInfo").put("name", "fake");
    request.put("durationMs", 1200);
    mvc.perform(
            worker(
                    post(
                        "/internal/gpu-jobs/{jobId}/result",
                        (String) JsonPath.read(claimed, "$.jobId")))
                .content(json.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  /** LLM 결과 {content: "<JSON 문자열>"}. */
  Object llm(Object content) {
    ObjectNode result = json.createObjectNode();
    result.put("content", json.writeValueAsString(content));
    result.put("evalCount", 321);
    return result;
  }

  static Object transcript() {
    return java.util.Map.of(
        "language",
        "ko",
        "segments",
        List.of(
            java.util.Map.of(
                "speaker", "SPEAKER_01", "start", 1.0, "end", 3.5, "text", "내 접시 누가 다 먹었어?"),
            java.util.Map.of(
                "speaker", "SPEAKER_00", "start", 4.0, "end", 6.0, "text", "나 아니야, 진짜로."),
            java.util.Map.of(
                "speaker", "SPEAKER_01", "start", 6.5, "end", 9.0, "text", "입가에 소스 묻었는데?")));
  }

  static Object extracted() {
    return java.util.Map.of(
        "candidates",
        List.of(
            java.util.Map.of(
                "kind",
                "FUNNY",
                "start",
                1.0,
                "end",
                9.0,
                "summary",
                "빈 접시 범인 찾기",
                "reason",
                "들통나는 반전",
                "story",
                5,
                "visual",
                4,
                "relatable",
                4,
                "intro",
                false)));
  }

  static java.util.Map<String, Object> script(int panels) {
    List<Object> list = new java.util.ArrayList<>();
    String[] roles = {"기", "승", "전", "결"};
    String[] shots = {"wide", "medium", "close-up", "medium"};
    String[] scenes = {
      "{A} and {B} sit at table, restaurant",
      "{B} secretly eats from plate",
      "{A} points at empty plate, shocked",
      "{B} laughs, {A} sighs"
    };
    // 대사는 장면 구간의 실제 말이어야 남는다 (ScriptHandler가 장면 밖 대사를 버린다)
    String[] spoken = {"내 접시 누가 다 먹었어?", "나 아니야, 진짜로.", "입가에 소스 묻었는데?"};
    for (int i = 0; i < panels; i++) {
      list.add(
          java.util.Map.of(
              "role",
              roles[i],
              "shot",
              shots[i],
              "scene",
              scenes[i],
              "lines",
              i < spoken.length
                  ? List.of(java.util.Map.of("speaker", i % 2 == 0 ? "A" : "B", "text", spoken[i]))
                  : List.of()));
    }
    return java.util.Map.of(
        "title",
        "접시 미스터리",
        "characters",
        List.of(
            java.util.Map.of(
                "speaker",
                "A",
                "name",
                "하린",
                "persona",
                "먹는 데 진심",
                "look",
                "girl, short black hair, yellow hoodie"),
            java.util.Map.of(
                "speaker", "B", "name", "준호", "persona", "능청꾼", "look", "boy, glasses")),
        "panels",
        list,
        "nameWarnings",
        List.of("민지"));
  }

  static byte[] png() throws Exception {
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    }
  }

  @Test
  @DisplayName("FR-001~013: 녹음부터 만화함 등록까지 자동으로 끝난다 (콘티 규칙 위반은 다시 시도)")
  void wholePipeline() throws Exception {
    String owner = PublicId.generate();
    String comicId = PublicId.generate();
    when(content.register(any())).thenReturn(new ContentClient.Registered(comicId));

    mvc.perform(
            as(post("/api/v1/conversations"), owner)
                .content("{\"source\":\"RECORDING\",\"consent\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"));

    String id =
        JsonPath.read(
            body(
                mvc.perform(
                        as(post("/api/v1/conversations"), owner)
                            .content(
                                "{\"source\":\"RECORDING\",\"consent\":true,\"language\":\"ko\","
                                    + "\"concept\":\"AUTO\"}"))
                    .andExpect(status().isCreated())
                    .andDo(
                        doc(
                            "conversations-create",
                            requestHeaders(USER),
                            requestFields(
                                fieldWithPath("source")
                                    .description("RECORDING(브라우저 녹음) 또는 UPLOAD(파일)"),
                                fieldWithPath("consent")
                                    .description(
                                        "함께 대화하는 사람들의 녹음 동의. true가 아니면 400 CONSENT_REQUIRED"),
                                fieldWithPath("language")
                                    .optional()
                                    .description("ko 또는 en. 없으면 자동 감지"),
                                fieldWithPath("concept")
                                    .optional()
                                    .description(
                                        "만화 컨셉. AUTO 또는 없으면 장면에 맞게 고른다. DAILY(일상 웹툰), ANIMALS(동물 친구들), WATERCOLOR(수채화 애니메이션),"
                                            + " PICTURE_BOOK(어린이 그림책), NEWSPAPER(흑백 신문 만화), JOSEON(조선시대 사람들), WUXIA(무협)")),
                            responseFields(
                                fieldWithPath("id").description("대화 ID"),
                                fieldWithPath("status").description("RECORDING"))))),
            "$.id");

    for (int i = 0; i < 2; i++) {
      ResultActions chunk =
          mvc.perform(
                  as(post("/api/v1/conversations/{id}/chunks", id), owner)
                      .content("{\"index\":" + i + ",\"contentType\":\"audio/webm\"}"))
              .andExpect(status().isOk());
      if (i == 0) {
        chunk.andDo(
            doc(
                "conversations-chunk",
                pathParameters(parameterWithName("id").description("대화 ID")),
                requestFields(
                    fieldWithPath("index").description("조각 번호 0~59 (1분 조각 기준 최대 1시간)"),
                    fieldWithPath("contentType")
                        .description("audio/webm, audio/ogg, audio/mp4, audio/mpeg, audio/wav 등")),
                responseFields(
                    fieldWithPath("putUrl").description("사전 서명 PUT 주소. web 서버가 조각을 이 주소로 올린다"),
                    fieldWithPath("expiresIn").description("유효 시간(초)"))));
      }
      put(JsonPath.read(body(chunk), "$.putUrl"), new byte[] {1, 2, 3, 4});
    }
    mvc.perform(
            as(post("/api/v1/conversations/{id}/chunks", id), owner)
                .content("{\"index\":2,\"contentType\":\"video/mp4\"}"))
        .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_AUDIO"));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/chunks", id), owner)
                .content("{\"index\":60,\"contentType\":\"audio/webm\"}"))
        .andExpect(jsonPath("$.error.code").value("TOO_LONG"));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":3}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUDIO_MISSING"));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":61}"))
        .andExpect(jsonPath("$.error.code").value("TOO_LONG"));

    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":2}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("TRANSCRIBING"))
        .andDo(
            doc(
                "conversations-finish",
                pathParameters(parameterWithName("id").description("대화 ID")),
                requestFields(
                    fieldWithPath("chunkCount")
                        .description("올린 조각 수. 모두 올라와 있어야 한다(없으면 400 AUDIO_MISSING)")),
                responseFields(fieldWithPath("status").description("처리 상태"))));
    mvc.perform(
            as(post("/api/v1/conversations/{id}/finish", id), owner).content("{\"chunkCount\":2}"))
        .andExpect(status().isConflict());

    // 워커가 연락한 적이 없으면 GPU를 기다리는 중으로 보여 준다
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.status").value("GPU_WAITING"))
        .andExpect(jsonPath("$.stage").value("TRANSCRIBING"))
        .andDo(doc("conversations-get-waiting", responseFields(VIEW)));
    mvc.perform(as(get("/api/v1/conversations/{id}", id), PublicId.generate()))
        .andExpect(status().isNotFound());

    // TRANSCRIBE
    String transcribe =
        body(
            mvc.perform(
                    worker(post("/internal/gpu-jobs/claim"))
                        .content(
                            "{\"types\":[\"TRANSCRIBE\",\"LLM\",\"IMAGE\"],\"loadedModel\":\"WHISPER\",\"version\":\"0.1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("TRANSCRIBE"))
                .andExpect(jsonPath("$.input.audioUrls.length()").value(2))
                .andExpect(jsonPath("$.input.language").value("ko"))
                .andDo(
                    doc(
                        "gpu-jobs-claim",
                        requestHeaders(WORKER_ID),
                        requestFields(
                            fieldWithPath("types")
                                .description("받을 수 있는 작업 종류 (TRANSCRIBE, LLM, IMAGE)"),
                            fieldWithPath("loadedModel")
                                .optional()
                                .description("지금 올려 둔 모델 묶음 (WHISPER, LLM, SDXL). 같은 종류를 먼저 준다"),
                            fieldWithPath("version").optional().description("워커 버전")),
                        responseFields(
                            fieldWithPath("jobId").description("작업 ID"),
                            fieldWithPath("type").description("작업 종류"),
                            fieldWithPath("leaseToken")
                                .description("임대 토큰. heartbeat·result·fail에 함께 보낸다"),
                            fieldWithPath("leaseExpiresAt")
                                .description("임대 만료 시각 (90초, heartbeat로 연장)"),
                            fieldWithPath("deadlineAt")
                                .description("최대 실행 시각 (TRANSCRIBE 20분, LLM 5분, IMAGE 10분)"),
                            subsectionWithPath("input")
                                .description("작업 입력. 객체 주소는 사전 서명 주소(유효 시간 = 마감까지)")))));
    String jobId = JsonPath.read(transcribe, "$.jobId");
    String token = JsonPath.read(transcribe, "$.leaseToken");

    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.status").value("TRANSCRIBING"));

    mvc.perform(
            worker(post("/internal/gpu-jobs/{jobId}/heartbeat", jobId))
                .content("{\"leaseToken\":\"" + token + "\",\"progress\":40}"))
        .andExpect(status().isOk())
        .andDo(
            doc(
                "gpu-jobs-heartbeat",
                pathParameters(parameterWithName("jobId").description("작업 ID")),
                requestFields(
                    fieldWithPath("leaseToken").description("임대 토큰"),
                    fieldWithPath("progress").optional().description("진행률 (참고용)")),
                responseFields(fieldWithPath("leaseExpiresAt").description("연장된 임대 만료 시각"))));
    mvc.perform(
            worker(post("/internal/gpu-jobs/{jobId}/heartbeat", jobId))
                .content("{\"leaseToken\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("LEASE_LOST"))
        .andDo(doc("gpu-jobs-lease-lost"));

    ObjectNode resultBody = json.createObjectNode();
    resultBody.put("leaseToken", token);
    resultBody.set("result", json.valueToTree(transcript()));
    resultBody.putObject("modelInfo").put("whisper", "large-v3");
    resultBody.put("durationMs", 52000);
    mvc.perform(
            worker(post("/internal/gpu-jobs/{jobId}/result", jobId))
                .content(json.writeValueAsString(resultBody)))
        .andExpect(status().isOk())
        .andDo(
            doc(
                "gpu-jobs-result",
                pathParameters(parameterWithName("jobId").description("작업 ID")),
                requestFields(
                    fieldWithPath("leaseToken").description("임대 토큰. 같은 토큰으로 다시 보내도 200"),
                    subsectionWithPath("result").description("작업 결과 (종류별 형식은 아래)"),
                    subsectionWithPath("modelInfo").description("쓴 모델 정보"),
                    fieldWithPath("durationMs").description("실행 시간(ms)"))));
    mvc.perform(
            worker(post("/internal/gpu-jobs/{jobId}/result", jobId))
                .content(json.writeValueAsString(resultBody)))
        .andExpect(status().isOk());
    String conversationId = id;
    assertThat(storage.exists(WorkKeys.audioChunk(conversationId, 0))).isFalse();

    // EXTRACT (구간 1개) → SELECT(서버) → SCRIPT
    String extract = claim();
    assertThat((String) JsonPath.read(extract, "$.input.prompt")).contains("A: 내 접시 누가 다 먹었어?");
    result(extract, llm(extracted()));

    mvc.perform(as(get("/api/v1/conversations/{id}/candidates", id), owner))
        .andExpect(jsonPath("$.candidates[0].selected").value(true))
        .andDo(
            doc(
                "conversations-candidates",
                responseFields(
                    fieldWithPath("candidates[].kind")
                        .description("CORE(대화의 핵심 주제), FUNNY(웃음 포인트), IMPORTANT(결정·약속·소식·감정)"),
                    fieldWithPath("candidates[].start").description("시작(초)"),
                    fieldWithPath("candidates[].end").description("끝(초)"),
                    fieldWithPath("candidates[].summary").description("한 줄 요약"),
                    fieldWithPath("candidates[].reason").description("고른 이유"),
                    fieldWithPath("candidates[].score")
                        .description("점수 0~1: (이야기성 0.4 + 그림 0.3 + 공감 0.3) / 5, 도입부는 0.3 감점"),
                    fieldWithPath("candidates[].story").description("이야기성 1~5").optional(),
                    fieldWithPath("candidates[].visual").description("그림으로 보이는가 1~5").optional(),
                    fieldWithPath("candidates[].relatable").description("공감 1~5").optional(),
                    fieldWithPath("candidates[].intro")
                        .description("인사·안부 같은 도입부면 true")
                        .optional(),
                    fieldWithPath("candidates[].selected").description("만화로 만든 장면이면 true"))));

    // SCRIPT: 컷이 3개뿐인 콘티는 규칙 위반으로 다시 시도한다
    result(claim(), llm(script(3)));
    String script = claim();
    assertThat((String) JsonPath.read(script, "$.input.system")).contains("4컷");
    result(script, llm(script(4)));

    // REVIEW는 서버에서 규칙만 다시 확인한다 (LLM 호출 없음)

    // DRAW: 워커가 사전 서명 PUT 주소로 그림을 올린다
    String draw = claim();
    List<String> urls = JsonPath.read(draw, "$.input.images[*].uploadUrl");
    assertThat(urls).hasSize(4);
    // CLIP은 앞 77토큰만 읽으므로 짧은 그림체 → 구도 → 장면 순이고, {A}는 외모로 바뀐다
    assertThat((String) JsonPath.read(draw, "$.input.images[0].prompt"))
        .startsWith("simple cartoon")
        .containsSubsequence("wide shot", "(girl, short black hair, yellow hoodie) and (boy");
    // 네 컷을 같은 시드로 그려 인물이 컷마다 바뀌지 않게 한다
    List<Number> seeds = JsonPath.read(draw, "$.input.images[*].seed");
    assertThat(seeds).hasSize(4);
    assertThat(seeds.stream().map(Number::longValue).distinct()).hasSize(1);
    assertThat((Integer) JsonPath.read(draw, "$.input.images[0].width")).isEqualTo(768);
    for (String url : urls) {
      put(url, png());
    }
    result(draw, java.util.Map.of("uploaded", List.of("panel-1", "panel-2", "panel-3", "panel-4")));

    // COMPOSE는 서버에서 따로 실행된다
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
                    .andExpect(jsonPath("$.status").value("DONE")));
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.comicId").value(comicId))
        .andExpect(jsonPath("$.retries").value(1))
        .andExpect(jsonPath("$.progress").value(100))
        .andExpect(jsonPath("$.durationSec").value(9))
        .andDo(
            doc(
                "conversations-get",
                pathParameters(parameterWithName("id").description("대화 ID")),
                responseFields(VIEW)));

    ArgumentCaptor<ContentClient.RegisterComic> registered =
        ArgumentCaptor.forClass(ContentClient.RegisterComic.class);
    verify(content).register(registered.capture());
    assertThat(registered.getValue().nameWarnings()).containsExactly("민지");
    assertThat(registered.getValue().ownerId()).isEqualTo(owner);
    byte[] full = storage.get(WorkKeys.comicFull(conversationId)).orElseThrow();
    BufferedImage composed = ImageIO.read(new java.io.ByteArrayInputStream(full));
    assertThat(composed.getWidth()).isEqualTo(800);

    mvc.perform(
            post("/internal/gpu-jobs/claim")
                .header("X-Worker-Id", WORKER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"types\":[\"LLM\"]}"))
        .andExpect(status().isNoContent());

    mvc.perform(as(get("/api/v1/conversations"), owner))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andDo(
            doc(
                "conversations-list",
                responseFields(
                        fieldWithPath("nextCursor")
                            .type(JsonFieldType.STRING)
                            .optional()
                            .description("다음 페이지 커서. 마지막이면 null"))
                    .andWithPrefix("items[].", VIEW)));

    mvc.perform(as(delete("/api/v1/conversations/{id}", id), owner))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "conversations-delete",
                pathParameters(parameterWithName("id").description("대화 ID"))));
    assertThat(storage.exists(WorkKeys.transcript(conversationId))).isFalse();
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("FR-011: 다시 해도 되는 실패는 대기열로, 아니면 단계 재시도, 3번 실패하면 FAILED")
  void failures() throws Exception {
    String owner = PublicId.generate();
    String id = startedConversation(owner);

    String first = claim();
    mvc.perform(
            worker(
                    post(
                        "/internal/gpu-jobs/{jobId}/fail",
                        (String) JsonPath.read(first, "$.jobId")))
                .content(
                    "{\"leaseToken\":\""
                        + JsonPath.read(first, "$.leaseToken")
                        + "\",\"code\":\"VRAM_BUSY\",\"message\":\"VRAM 부족\",\"retryable\":true}"))
        .andExpect(status().isOk())
        .andDo(
            doc(
                "gpu-jobs-fail",
                pathParameters(parameterWithName("jobId").description("작업 ID")),
                requestFields(
                    fieldWithPath("leaseToken").description("임대 토큰"),
                    fieldWithPath("code").description("실패 코드 (예: VRAM_BUSY, MODEL_ERROR)"),
                    fieldWithPath("message").description("실패 내용"),
                    fieldWithPath("retryable").description("true면 같은 작업을 대기열로 되돌린다(임대 3번까지)"))));
    // 같은 작업이 다시 나온다
    String again = claim();
    assertThat((String) JsonPath.read(again, "$.jobId")).isEqualTo(JsonPath.read(first, "$.jobId"));

    String current = again;
    for (int attempt = 1; attempt <= 3; attempt++) {
      mvc.perform(
              worker(
                      post(
                          "/internal/gpu-jobs/{jobId}/fail",
                          (String) JsonPath.read(current, "$.jobId")))
                  .content(
                      "{\"leaseToken\":\""
                          + JsonPath.read(current, "$.leaseToken")
                          + "\",\"code\":\"MODEL_ERROR\",\"message\":\"오류\",\"retryable\":false}"))
          .andExpect(status().isOk());
      if (attempt < 3) {
        current = claim();
      }
    }
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.retries").value(2))
        .andExpect(jsonPath("$.failureReason").isNotEmpty());
    mvc.perform(
            post("/internal/gpu-jobs/claim")
                .header("X-Worker-Id", WORKER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"types\":[\"TRANSCRIBE\"]}"))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("임대가 끝났는데 소식이 없으면 대기열로 되돌리고, 3번 넘으면 단계를 다시 한다")
  void leaseRecovery(@Autowired net.java21.fourcut.conversation.pipeline.PipelineService pipeline)
      throws Exception {
    String owner = PublicId.generate();
    String id = startedConversation(owner);
    String job = null;
    for (int lease = 1; lease <= 3; lease++) {
      job = claim();
      jdbc.update(
          "update gpu_jobs set lease_expires_at = now() - interval '1 minute' where state = 'LEASED'");
      recover();
    }
    // 3번째 임대가 만료되면 작업은 LEASE_EXHAUSTED로 실패하고 단계가 2번째 시도로 새 작업을 만든다
    Integer attempts =
        jdbc.queryForObject(
            "select max(ps.attempt) from pipeline_steps ps join conversations c on c.id ="
                + " ps.conversation_id where c.public_id = ?",
            Integer.class,
            id);
    assertThat(attempts).isEqualTo(2);
    String code =
        jdbc.queryForObject(
            "select error_code from gpu_jobs where id = ?::bigint",
            String.class,
            (String) JsonPath.read(job, "$.jobId"));
    assertThat(code).isEqualTo("LEASE_EXHAUSTED");
    // 만료된 임대로는 결과를 낼 수 없다
    mvc.perform(
            worker(
                    post(
                        "/internal/gpu-jobs/{jobId}/result",
                        (String) JsonPath.read(job, "$.jobId")))
                .content(
                    "{\"leaseToken\":\""
                        + JsonPath.read(job, "$.leaseToken")
                        + "\",\"result\":{}}"))
        .andExpect(status().isConflict());
    Long internalId =
        jdbc.queryForObject("select id from conversations where public_id = ?", Long.class, id);
    assertThat(pipeline.retries(internalId)).isEqualTo(1);
  }

  @Autowired private org.springframework.context.ApplicationContext context;

  void recover() {
    net.java21.fourcut.conversation.pipeline.PipelineTestSupport.recover(context);
  }

  @Test
  @DisplayName("Edge: 장면 후보가 없으면 NO_SCENE")
  void noScene() throws Exception {
    String owner = PublicId.generate();
    String id = startedConversation(owner);
    result(claim(), transcript());
    result(claim(), llm(java.util.Map.of("candidates", List.of())));
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner))
        .andExpect(jsonPath("$.status").value("NO_SCENE"))
        .andExpect(jsonPath("$.failureReason").value("만화로 만들 장면을 찾지 못했어요."));
  }

  @Test
  @DisplayName("M-03: 탈퇴하면 대화와 중간 결과를 모두 지운다 (멱등)")
  void withdrawal() throws Exception {
    String owner = PublicId.generate();
    String id = startedConversation(owner);
    mvc.perform(delete("/internal/users/{userId}/data", owner))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "internal-user-data-delete",
                pathParameters(parameterWithName("userId").description("탈퇴한 사용자 ID"))));
    mvc.perform(delete("/internal/users/{userId}/data", owner)).andExpect(status().isNoContent());
    assertThat(storage.exists(WorkKeys.audioChunk(id, 0))).isFalse();
    mvc.perform(as(get("/api/v1/conversations/{id}", id), owner)).andExpect(status().isNotFound());
  }
}
