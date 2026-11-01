package net.java21.fourcut.conversation.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;

/**
 * 커서: 정렬 키(시각 + id)를 JSON으로 만들어 Base64URL로 인코딩한 불투명 문자열 (docs api-conventions 6절). 클라이언트는 해석하지 않는다.
 *
 * @param at 정렬 시각
 * @param id 같은 시각일 때 두 번째 정렬 키 (내부 BIGINT id)
 */
public record Cursor(Instant at, long id) {

  /** 최신순 목록의 첫 페이지 (모든 행보다 뒤). */
  public static final Cursor NEWEST_FIRST =
      new Cursor(Instant.parse("9999-12-31T00:00:00Z"), Long.MAX_VALUE);

  /** 오래된 순 목록의 첫 페이지 (모든 행보다 앞). */
  public static final Cursor OLDEST_FIRST = new Cursor(Instant.parse("1970-01-01T00:00:00Z"), 0L);

  private static final Pattern FORMAT =
      Pattern.compile("^\\{\"t\":\"([^\"]+)\",\"id\":([0-9]{1,19})}$");

  public String encode() {
    String json = "{\"t\":\"" + at + "\",\"id\":" + id + "}";
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /** 커서를 풀거나, 없으면 첫 페이지 기준을 준다. 형식이 틀리면 400 VALIDATION_FAILED. */
  public static Cursor decodeOr(String cursor, Cursor first) {
    if (cursor == null || cursor.isBlank()) {
      return first;
    }
    try {
      String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      Matcher m = FORMAT.matcher(json);
      if (!m.matches()) {
        throw invalid(null);
      }
      return new Cursor(Instant.parse(m.group(1)), Long.parseLong(m.group(2)));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      throw invalid(e);
    }
  }

  private static ApiException invalid(Exception cause) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, "cursor 형식이 올바르지 않아요.", cause);
  }
}
