package net.java21.fourcut.conversation.support;

import java.util.Locale;

/** pgvector SQL 도우미. 캐릭터 설명 임베딩과 배경음 임베딩이 함께 쓴다. */
public final class Vectors {

  private Vectors() {}

  /** float 배열 → pgvector 글자 표기 {@code [0.5,-1.0]}. */
  public static String literal(float[] v) {
    StringBuilder sb = new StringBuilder(v.length * 10).append('[');
    for (int i = 0; i < v.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(String.format(Locale.ROOT, "%.7g", v[i]));
    }
    return sb.append(']').toString();
  }

  /** 스키마 이름을 SQL 식별자로 감싼다 (확장이 검색 경로 밖 스키마에 있어도 쓸 수 있게). */
  public static String quote(String identifier) {
    return '"' + identifier.replace("\"", "\"\"") + '"';
  }
}
