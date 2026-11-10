package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.java21.fourcut.conversation.pipeline.ConversationInfo;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.pipeline.JobType;
import net.java21.fourcut.conversation.pipeline.Step;
import net.java21.fourcut.conversation.pipeline.StepContext;
import net.java21.fourcut.conversation.pipeline.StepOutcome;
import net.java21.fourcut.conversation.scenario.ComicScript;
import net.java21.fourcut.conversation.scenario.Motion;
import net.java21.fourcut.conversation.scenario.ScenarioStore;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.WorkKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class MotionPlanTest {

  private static final String ID = "c1";

  private final JsonMapper json = JsonMapper.builder().build();
  private final DirectLlm llm = mock(DirectLlm.class);
  private final MotionPlan plan = new MotionPlan(llm, json);

  private static final String ANSWER =
      """
      {"panels":[{"panel":1,"target":"the wall clock above the door",\
      "states":["the minute hand of the wall clock points to twelve",\
      "the minute hand of the wall clock points to one",\
      "the minute hand of the wall clock points to two."]},\
      {"panel":2,"target":"his coffee cup","states":["a","b","c"]}]}""";

  @Test
  @DisplayName("FR-051: 넓은 그림을 보여 주고 받은 답에서 컷마다 움직일 것과 세 장면을 읽는다")
  void readsPlan() {
    when(llm.available()).thenReturn(true);
    when(llm.see(eq(DirectLlm.MOTION), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Optional.of(ANSWER));

    Map<Integer, MotionPlan.Plan> plans =
        plan.plan(
            List.of(
                new MotionPlan.Asked(0, png(), Motion.BREEZE),
                new MotionPlan.Asked(1, png(), Motion.RAIN)));

    assertThat(plans).containsOnlyKeys(0);
    MotionPlan.Plan p = plans.get(0);
    assertThat(p.target()).isEqualTo("the wall clock above the door");
    assertThat(p.states().get(2)).isEqualTo("the minute hand of the wall clock points to two");
    assertThat(p.change(1))
        .startsWith("change the wall clock above the door so that the minute hand")
        .contains("stays exactly as it is")
        .doesNotContainPattern("(?i)\\b(no|not|leaves|wind)\\b");
  }

  @Test
  @DisplayName("FR-051: 인물을 움직이거나 장면이 셋이 아니거나 너무 짧은 답은 쓰지 않는다")
  void rejectsBadPlans() {
    String ok = "steam rises softly from the soup pot";
    assertThat(MotionPlan.usable("the soup pot", List.of(ok, ok + " higher", ok + " in curls")))
        .isTrue();
    assertThat(MotionPlan.usable("her hair", List.of(ok, ok + " a", ok + " b"))).isFalse();
    assertThat(MotionPlan.usable("the soup pot", List.of(ok, ok + " a"))).isFalse();
    assertThat(MotionPlan.usable("the soup pot", List.of(ok, ok, ok))).isFalse();
    assertThat(MotionPlan.usable("the soup pot", List.of("steam", ok, ok + " b"))).isFalse();
    assertThat(MotionPlan.usable("", List.of(ok, ok + " a", ok + " b"))).isFalse();
  }

  @Test
  @DisplayName("FR-051: 질문 글에 컷 번호와 콘티 글로 정한 움직임(가벼운 움직임 빼고)을 참고로 적는다")
  void prompt() {
    String prompt =
        MotionPlan.prompt(
            List.of(
                new MotionPlan.Asked(0, new byte[0], Motion.BREEZE),
                new MotionPlan.Asked(2, new byte[0], Motion.RAIN)));
    assertThat(prompt).contains("panel 1\n", "panel 3, hint rain");
  }

  @Test
  @DisplayName("FR-051: 외부 LLM을 부를 수 없거나 답이 깨지면 빈 값(콘티 글로 정한 움직임을 쓴다)")
  void unavailable() {
    when(llm.available()).thenReturn(false);
    assertThat(plan.plan(List.of(new MotionPlan.Asked(0, png(), Motion.BREEZE)))).isEmpty();
    verify(llm, never()).see(any(), any(), any(), any(), any());

    when(llm.available()).thenReturn(true);
    when(llm.see(any(), any(), any(), any(), any())).thenReturn(Optional.of("not json"));
    assertThat(plan.plan(List.of(new MotionPlan.Asked(0, png(), Motion.BREEZE)))).isEmpty();
    assertThat(plan.plan(List.of(new MotionPlan.Asked(0, new byte[] {1}, Motion.BREEZE))))
        .isEmpty();
  }

  @Test
  @DisplayName("FR-051: 그림을 다 그리면 서버 작업으로 넘기고, 넓은 그림을 보고 정한 움직임만 따로 그리게 한다")
  void drawThenMotion() {
    ScenarioStore scenarios = mock(ScenarioStore.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    ComicScript script = script();
    when(scenarios.script(ID)).thenReturn(script);
    when(storage.exists(any())).thenReturn(true);
    when(storage.get(WorkKeys.panelWide(ID, 1))).thenReturn(Optional.of(png()));
    when(storage.get(WorkKeys.panelWide(ID, 2))).thenReturn(Optional.empty());
    when(llm.available()).thenReturn(true);
    when(llm.see(any(), any(), any(), any(), any())).thenReturn(Optional.of(ANSWER));
    var handler =
        new DrawHandler(
            scenarios,
            storage,
            properties(true),
            json,
            mock(net.java21.fourcut.conversation.character.CharacterLibrary.class),
            plan);

    ObjectNode drawPayload = json.createObjectNode();
    drawPayload.putArray("images").addObject().put("name", "panel-1");
    drawPayload.withArray("images").addObject().put("name", "panel-2");
    JsonNode uploaded = json.readTree("{\"uploaded\":[\"panel-1\",\"panel-2\"]}");
    assertThat(handler.onResult(context(drawPayload), uploaded))
        .isInstanceOf(StepOutcome.RunLocal.class);

    StepOutcome next = handler.runLocal(context(null));
    assertThat(next).isInstanceOf(StepOutcome.Enqueue.class);
    StepOutcome.Enqueue e = (StepOutcome.Enqueue) next;
    assertThat(e.type()).isEqualTo(JobType.IMAGE);
    assertThat(e.payload().path(DrawHandler.MOTION_ONLY).asBoolean()).isTrue();
    JsonNode images = e.payload().path("images");
    assertThat(images).hasSize(1);
    JsonNode image = images.get(0);
    assertThat(image.path("baseGetKey").asString()).isEqualTo(WorkKeys.panelWide(ID, 1));
    assertThat(image.path("depthGetKey").asString()).isEqualTo(WorkKeys.panelDepth(ID, 1));
    assertThat(image.path("width").asInt()).isEqualTo(1024);
    JsonNode motion = image.path("motion");
    assertThat(motion.path("target").asString()).isEqualTo("the wall clock above the door");
    assertThat(motion.path("prompt").asString()).contains("points to twelve");
    assertThat(motion.path("next").path("prompt").asString()).contains("points to one");
    assertThat(motion.path("next").path("next").path("prompt").asString())
        .contains("points to two");
    assertThat(motion.path("next").path("next").path("uploadPutKey").asString())
        .isEqualTo(WorkKeys.panelD(ID, 1));

    // 움직임 그림 작업이 끝나거나 실패하면 그림을 다시 그리지 않고 대사 읽기로 간다
    assertThat(handler.onResult(context(e.payload()), json.createObjectNode()))
        .isEqualTo(new StepOutcome.Done(WorkKeys.workPrefix(ID), Step.SPEAK));
    assertThat(handler.onJobFailed(context(e.payload()), "x"))
        .isEqualTo(new StepOutcome.Done(WorkKeys.workPrefix(ID), Step.SPEAK));
    assertThat(handler.onJobFailed(context(drawPayload), "x"))
        .isInstanceOf(StepOutcome.Retry.class);
    assertThat(handler.onStalled(context(drawPayload)))
        .isEqualTo(new StepOutcome.Done(WorkKeys.workPrefix(ID), Step.SPEAK));
  }

  @Test
  @DisplayName("FR-051: 그림을 보고 정하지 못한 컷은 콘티 글로 정한 움직임으로, 넓은 그림이 없거나 움직임이 없으면 바로 다음 단계")
  void fallbacks() {
    ScenarioStore scenarios = mock(ScenarioStore.class);
    ObjectStorage storage = mock(ObjectStorage.class);
    when(scenarios.script(ID)).thenReturn(script());
    when(storage.get(any())).thenReturn(Optional.of(png()));
    when(llm.available()).thenReturn(false);
    var handler = new DrawHandler(scenarios, storage, properties(true), json, null, plan);

    StepOutcome.Enqueue e = (StepOutcome.Enqueue) handler.runLocal(context(null));
    JsonNode images = e.payload().path("images");
    assertThat(images).hasSize(2);
    assertThat(images.get(0).has("depthGetKey")).isFalse();
    assertThat(images.get(1).path("motion").path("prompt").asString())
        .endsWith(Motion.RAIN.change());

    when(storage.get(any())).thenReturn(Optional.empty());
    assertThat(handler.runLocal(context(null)))
        .isEqualTo(new StepOutcome.Done(WorkKeys.workPrefix(ID), Step.SPEAK));

    // 예전 그림 작업(움직임 그림을 함께 그린 것)은 다시 그리지 않는다
    ObjectNode old = json.createObjectNode();
    old.putArray("images").addObject().put("name", "panel-1").putObject("motion");
    assertThat(DrawHandler.motionDrawn(old)).isTrue();
    assertThat(DrawHandler.motionDrawn(json.createObjectNode())).isFalse();
  }

  private static StepContext context(JsonNode payload) {
    return new StepContext(
        new ConversationInfo(1L, ID, "u", "ko", 1, null, null, false), Step.DRAW, 1, payload);
  }

  private static ComicProperties properties(boolean twoPass) {
    return new ComicProperties(
        768,
        ComicProperties.DEFAULT_STYLE,
        ComicProperties.DEFAULT_NEGATIVE,
        "http://localhost",
        0.2,
        3,
        0.0,
        0,
        twoPass);
  }

  private static ComicScript script() {
    return new ComicScript(
        "t",
        List.of(new ComicScript.Character("A", "민지", "", "girl, short hair")),
        List.of(
            new ComicScript.Panel(
                "기",
                "wide",
                "{A} works",
                List.of(),
                "office, wall clock",
                List.of(),
                "사무실",
                "breeze"),
            new ComicScript.Panel(
                "승", "wide", "{A} walks", List.of(), "rainy street, rain", List.of(), "비", "rain")),
        true,
        List.of());
  }

  private static byte[] png() {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(64, 36, BufferedImage.TYPE_INT_RGB), "png", out);
      return out.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
