package net.java21.fourcut.conversation.support;

import java.util.List;
import java.util.function.Function;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;

/**
 * 커서 페이지 응답 {@code {items, nextCursor}}. nextCursor가 null이면 마지막 페이지다.
 *
 * @param nextCursor 다음 페이지 커서. 마지막이면 null (생략하지 않는다)
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 50;

  /** size+1개를 읽어 다음 페이지가 있는지 본다. */
  public static <E, T> CursorPage<T> of(
      List<E> rows, int size, Function<E, Cursor> cursorOf, Function<List<E>, List<T>> mapper) {
    boolean more = rows.size() > size;
    List<E> page = more ? rows.subList(0, size) : rows;
    String next = more ? cursorOf.apply(page.get(page.size() - 1)).encode() : null;
    return new CursorPage<>(mapper.apply(page), next);
  }

  /** size 파라미터 확인 (기본 20, 최대 50). */
  public static int size(Integer size) {
    if (size == null) {
      return DEFAULT_SIZE;
    }
    if (size < 1 || size > MAX_SIZE) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "size는 1~50이어야 해요.");
    }
    return size;
  }
}
