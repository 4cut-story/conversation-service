package net.java21.fourcut.conversation.scenario;

import java.util.List;
import java.util.Set;

/**
 * 4컷 콘티 (docs specs/001 pipeline-schemas.md). 규칙: 컷 4개(기·승·전·결), 컷당 대사 2문장 이내, 대화와 같은 언어, 실명 없음.
 *
 * @param nameWarnings 검수에서 찾은 실명 의심 단어. 올리기 전 경고에 쓴다
 */
public record ComicScript(
    String title,
    List<Character> characters,
    List<Panel> panels,
    Boolean reviewed,
    List<String> nameWarnings) {

  /** 등장인물. speaker는 전사의 가명(A, B…), look은 그림용 외모 설명. */
  public record Character(String speaker, String look) {}

  /** 컷 하나. role은 기·승·전·결. */
  public record Panel(String role, String scene, List<Line> lines) {}

  /** 말풍선 하나. */
  public record Line(String speaker, String text) {}

  /** 컷당 최대 말풍선 수. */
  public static final int MAX_LINES = 2;

  private static final List<String> ROLES = List.of("기", "승", "전", "결");

  /** 규칙 위반 내용. 없으면 null. */
  public String violation() {
    if (title == null || title.isBlank() || title.length() > 100) {
      return "제목이 없거나 100자를 넘는다";
    }
    if (characters == null || characters.isEmpty() || panels == null || panels.size() != 4) {
      return "등장인물이 없거나 컷이 4개가 아니다";
    }
    Set<String> speakers = Set.copyOf(characters.stream().map(Character::speaker).toList());
    for (int i = 0; i < 4; i++) {
      Panel p = panels.get(i);
      if (!ROLES.get(i).equals(p.role()) || p.scene() == null || p.scene().isBlank()) {
        return (i + 1) + "번 컷의 역할이나 장면 설명이 틀렸다";
      }
      List<Line> lines = p.lines() == null ? List.of() : p.lines();
      if (lines.size() > MAX_LINES) {
        return (i + 1) + "번 컷 대사가 2개를 넘는다";
      }
      for (Line l : lines) {
        if (l.text() == null || l.text().isBlank() || !speakers.contains(l.speaker())) {
          return (i + 1) + "번 컷 대사의 화자나 내용이 틀렸다";
        }
      }
    }
    return null;
  }

  ComicScript reviewedWith(List<String> warnings) {
    return new ComicScript(
        title, characters, panels, true, warnings == null ? List.of() : warnings);
  }
}
