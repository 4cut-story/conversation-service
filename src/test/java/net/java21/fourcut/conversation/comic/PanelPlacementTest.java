package net.java21.fourcut.conversation.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.comic.Staging.Side;
import net.java21.fourcut.conversation.pipeline.DirectLlm;
import net.java21.fourcut.conversation.scenario.ComicScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class PanelPlacementTest {

  private final DirectLlm llm = mock(DirectLlm.class);
  private final PanelPlacement placement = new PanelPlacement(llm, JsonMapper.builder().build());

  private static ComicScript script() {
    List<ComicScript.Character> cast =
        List.of(
            new ComicScript.Character("A", "민나", "", "boy, glasses"),
            new ComicScript.Character("B", "시온", "", "old man, white hair"));
    List<ComicScript.Panel> panels =
        List.of(
            new ComicScript.Panel("기", "wide", "{A} asks {B}", List.of(line("A"))),
            new ComicScript.Panel("승", "wide", "{A} smiles", List.of(line("A"))),
            new ComicScript.Panel("전", "wide", "{B} talks to {A}", List.of(line("B"))),
            new ComicScript.Panel("결", "wide", "{A} and {B} plant", List.of()));
    return new ComicScript("t", cast, panels, true, List.of());
  }

  private static ComicScript.Line line(String speaker) {
    return new ComicScript.Line(speaker, "말");
  }

  private static List<BufferedImage> images() {
    return List.of(img(), img(), img(), img());
  }

  private static BufferedImage img() {
    return new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
  }

  @Test
  @DisplayName("FR-006: 그림에서 두 인물이 바뀌어 서 있으면 말풍선 자리도 바꾼다")
  void swapped() {
    Map<String, Side> fixed =
        PanelPlacement.fix(
            Map.of("A", Side.LEFT, "B", Side.RIGHT), Map.of("A", "right", "B", "left"));
    assertThat(fixed).containsEntry("A", Side.RIGHT).containsEntry("B", Side.LEFT);
  }

  @Test
  @DisplayName("FR-006: 한 인물이 안 그려졌고 다른 인물이 그 자리에 서 있으면 안 보인 인물은 반대쪽이다")
  void absentFlips() {
    Map<String, Side> fixed =
        PanelPlacement.fix(
            Map.of("A", Side.LEFT, "B", Side.RIGHT), Map.of("A", "absent", "B", "left"));
    assertThat(fixed).containsEntry("A", Side.RIGHT).containsEntry("B", Side.LEFT);
  }

  @Test
  @DisplayName("FR-006: 두 사람이 같은 쪽이라는 답이나 빈 답은 믿지 않고 정해 둔 자리를 쓴다")
  void distrust() {
    Map<String, Side> planned = Map.of("A", Side.LEFT, "B", Side.RIGHT);
    assertThat(PanelPlacement.fix(planned, Map.of("A", "left", "B", "left"))).isEqualTo(planned);
    assertThat(PanelPlacement.fix(planned, Map.of())).isEqualTo(planned);
  }

  @Test
  @DisplayName("FR-006: 누가 말하는 컷은 그림을 보내 확인하고, 답대로 자리를 고친다")
  void check() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    assertThat(PanelPlacement.asked(s, planned, 4)).containsExactly(0, 1, 2);
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(
            Optional.of(
                "{\"panels\":[{\"panel\":1,\"people\":[{\"speaker\":\"A\",\"side\":\"right\"},"
                    + "{\"speaker\":\"B\",\"side\":\"left\"}]},{\"panel\":4,\"people\":[]}]}"));
    List<Map<String, Side>> out = placement.check(s, images(), planned);
    assertThat(out.get(0)).containsEntry("A", Side.RIGHT).containsEntry("B", Side.LEFT);
    assertThat(out.get(2)).isEqualTo(planned.get(2));
    assertThat(out.get(3)).isEqualTo(planned.get(3));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<byte[]>> sent = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(llm).see(anyString(), anyString(), prompt.capture(), anyString(), sent.capture());
    assertThat(sent.getValue()).hasSize(3);
    assertThat(prompt.getValue()).contains("(panel 1)", "(panel 3)", "old man, white hair");
  }

  @Test
  @DisplayName("FR-006: 테스트 전용 수동 처리 대화는 그림을 묻지 않고 정해 둔 자리 그대로다")
  void noAsk() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(true);
    assertThat(placement.locate(s, images(), planned, Map.of(), false).stages()).isEqualTo(planned);
    verify(llm, never()).see(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("FR-006: 외부 LLM을 부를 수 없거나 답이 없거나 깨졌으면 정해 둔 자리 그대로다")
  void fallback() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(false);
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    verify(llm, never()).see(any(), any(), any(), any(), any());

    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Optional.empty());
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Optional.of("not json"));
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("x"));
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
  }

  @Test
  @DisplayName("FR-007: 답에 얼굴 위치가 있으면 컷별 얼굴로 받고, 그림 밖이거나 없는 값은 버린다")
  void heads() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(
            Optional.of(
                "{\"panels\":[{\"panel\":1,\"people\":["
                    + "{\"speaker\":\"A\",\"side\":\"right\",\"x\":70,\"y\":40,\"size\":20},"
                    + "{\"speaker\":\"B\",\"side\":\"left\",\"x\":130,\"y\":40,\"size\":20}]},"
                    + "{\"panel\":2,\"people\":[{\"speaker\":\"A\",\"side\":\"center\"}]}]}"));
    PanelPlacement.Spots spots = placement.locate(s, images(), planned);
    assertThat(spots.heads()).hasSize(4);
    assertThat(spots.heads().get(0)).containsOnlyKeys("A");
    PanelPlacement.Head a = spots.heads().get(0).get("A");
    assertThat(a.x()).isEqualTo(0.7);
    assertThat(a.size()).isEqualTo(0.2);
    assertThat(spots.heads().get(1)).isEmpty();
    assertThat(spots.heads().get(3)).isEmpty();
    assertThat(spots.stages().get(0)).containsEntry("A", Side.RIGHT);
  }

  @Test
  @DisplayName("FR-007: 답에 가리면 안 되는 물건 상자가 있으면 컷별로 받고, 이상한 상자는 버리며 세 개까지만 쓴다")
  void props() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(
            Optional.of(
                "{\"panels\":[{\"panel\":1,\"people\":[{\"speaker\":\"A\",\"side\":\"right\"}],"
                    + "\"objects\":[{\"x\":30,\"y\":55,\"w\":20,\"h\":10},"
                    + "{\"x\":0,\"y\":0,\"w\":90,\"h\":90},{\"x\":80,\"y\":80,\"w\":40,\"h\":10},"
                    + "{\"x\":1,\"y\":1,\"w\":5,\"h\":5},{\"x\":2,\"y\":2,\"w\":5,\"h\":5},"
                    + "{\"x\":3,\"y\":3,\"w\":5,\"h\":5}]}]}"));
    PanelPlacement.Spots spots = placement.locate(s, images(), planned);
    assertThat(spots.props()).hasSize(4);
    assertThat(spots.props().get(0)).hasSize(3);
    assertThat(spots.props().get(0).get(0)).isEqualTo(new PanelPlacement.Prop(0.3, 0.55, 0.2, 0.1));
    assertThat(spots.props().get(1)).isEmpty();
    assertThat(PanelPlacement.fallback(planned).props()).isEmpty();
    assertThat(new PanelPlacement.Spots(planned, List.of()).props()).isEmpty();
  }

  @Test
  @DisplayName("FR-007: 그림 밖 위치, 너무 작거나 큰 얼굴, 안 보인 인물은 얼굴로 쓰지 않는다")
  void headRange() {
    var m = JsonMapper.builder().build();
    assertThat(PanelPlacement.head(m.readTree("{\"side\":\"left\",\"x\":50,\"y\":50,\"size\":10}")))
        .contains(new PanelPlacement.Head(0.5, 0.5, 0.1));
    assertThat(
            PanelPlacement.head(m.readTree("{\"side\":\"absent\",\"x\":50,\"y\":50,\"size\":10}")))
        .isEmpty();
    assertThat(PanelPlacement.head(m.readTree("{\"side\":\"left\",\"x\":50,\"y\":-1,\"size\":10}")))
        .isEmpty();
    assertThat(PanelPlacement.head(m.readTree("{\"side\":\"left\",\"x\":50,\"y\":50,\"size\":1}")))
        .isEmpty();
    assertThat(PanelPlacement.head(m.readTree("{\"side\":\"left\",\"x\":50,\"y\":50,\"size\":90}")))
        .isEmpty();
    assertThat(
            PanelPlacement.head(m.readTree("{\"side\":\"left\",\"x\":\"a\",\"y\":50,\"size\":9}")))
        .isEmpty();
  }

  @Test
  @DisplayName("FR-007: 확인을 못 하면 얼굴 없이 정해 둔 자리 그대로다")
  void locateFallback() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(false);
    PanelPlacement.Spots spots = placement.locate(s, images(), planned);
    assertThat(spots.stages()).isEqualTo(planned);
    assertThat(spots.heads()).hasSize(4).allMatch(Map::isEmpty);
  }

  private static byte[] pngBytes() throws java.io.IOException {
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(img(), "png", out);
    return out.toByteArray();
  }

  @Test
  @DisplayName("FR-007: 인물 참조 그림을 컷보다 먼저 보여 주고, 읽지 못하는 참조는 뺀다")
  @SuppressWarnings("unchecked")
  void references() throws Exception {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Optional.empty());
    placement.locate(s, images(), planned, Map.of("A", pngBytes(), "B", new byte[] {1, 2}));
    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<byte[]>> sent = ArgumentCaptor.forClass(List.class);
    verify(llm).see(anyString(), anyString(), prompt.capture(), anyString(), sent.capture());
    // 참조 1장(A) + 확인할 컷 3장(1·2·3컷)
    assertThat(sent.getValue()).hasSize(4);
    assertThat(prompt.getValue()).startsWith("처음 1장은 인물 참조 그림이다.").contains("참조 1: A (민나)");
    assertThat(PanelPlacement.refPrompt(s, Map.of())).isEmpty();
  }

  @Test
  @DisplayName("FR-007: 한 얼굴을 두 사람에게 준 답은 두 사람 다 얼굴을 빼고 그 얼굴만 피할 자리로 남긴다")
  void sameFace() {
    var one = new PanelPlacement.Head(0.5, 0.4, 0.3);
    var near = new PanelPlacement.Head(0.55, 0.42, 0.3);
    var far = new PanelPlacement.Head(0.2, 0.4, 0.2);
    assertThat(PanelPlacement.distinct(Map.of("A", one, "B", near)))
        .containsOnlyKeys(PanelPlacement.UNKNOWN);
    assertThat(PanelPlacement.distinct(Map.of("A", one, "B", far))).containsOnlyKeys("A", "B");
    ComicScript s = script();
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(
            Optional.of(
                "{\"panels\":[{\"panel\":1,\"people\":["
                    + "{\"speaker\":\"A\",\"side\":\"center\",\"x\":50,\"y\":40,\"size\":30},"
                    + "{\"speaker\":\"B\",\"side\":\"center\",\"x\":52,\"y\":40,\"size\":30}]}]}"));
    PanelPlacement.Spots spots = placement.locate(s, images(), Staging.panels(s));
    assertThat(spots.heads().get(0)).containsOnlyKeys(PanelPlacement.UNKNOWN);
  }
}
