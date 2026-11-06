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
  @DisplayName("FR-006: 두 명이 서고 누가 말하는 컷만 그림을 보내 확인하고, 답대로 자리를 고친다")
  void check() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    assertThat(PanelPlacement.asked(s, planned, 4)).containsExactly(0, 2);
    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyList()))
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
    verify(llm).see(anyString(), prompt.capture(), anyString(), sent.capture());
    assertThat(sent.getValue()).hasSize(2);
    assertThat(prompt.getValue()).contains("(panel 1)", "(panel 3)", "old man, white hair");
  }

  @Test
  @DisplayName("FR-006: 외부 LLM을 부를 수 없거나 답이 없거나 깨졌으면 정해 둔 자리 그대로다")
  void fallback() {
    ComicScript s = script();
    List<Map<String, Side>> planned = Staging.panels(s);
    when(llm.available()).thenReturn(false);
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    verify(llm, never()).see(any(), any(), any(), any());

    when(llm.available()).thenReturn(true);
    when(llm.see(anyString(), anyString(), anyString(), anyList())).thenReturn(Optional.empty());
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    when(llm.see(anyString(), anyString(), anyString(), anyList()))
        .thenReturn(Optional.of("not json"));
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
    when(llm.see(anyString(), anyString(), anyString(), anyList()))
        .thenThrow(new IllegalStateException("x"));
    assertThat(placement.check(s, images(), planned)).isEqualTo(planned);
  }
}
