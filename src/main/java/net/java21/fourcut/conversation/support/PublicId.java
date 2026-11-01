package net.java21.fourcut.conversation.support;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 밖에 보이는 ID (NanoID 형식, 21자, 알파벳 {@code A-Za-z0-9_-}). URL·API 응답·다른 서비스에는 이 값만 내보내고 DB의 BIGINT 기본
 * 키는 내보내지 않는다. 126비트 무작위라 추측할 수 없고, 겹칠 확률은 사실상 0이다(컬럼에 UNIQUE 제약이 있다).
 */
public final class PublicId {

  public static final int LENGTH = 21;

  private static final char[] ALPHABET =
      "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

  /** 형식 정규식. Bean Validation {@code @Pattern}에도 쓴다. */
  public static final String PATTERN = "^[A-Za-z0-9_-]{21}$";

  private static final Pattern FORMAT = Pattern.compile(PATTERN);
  private static final SecureRandom RANDOM = new SecureRandom();

  private PublicId() {}

  /** 새 ID. 알파벳이 64자라 바이트의 하위 6비트를 그대로 써도 치우침이 없다. */
  public static String generate() {
    byte[] bytes = new byte[LENGTH];
    RANDOM.nextBytes(bytes);
    char[] id = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      id[i] = ALPHABET[bytes[i] & 63];
    }
    return new String(id);
  }

  /** 형식이 맞는지. 경로 변수·헤더처럼 밖에서 온 값을 DB 조회 전에 거른다. */
  public static boolean isValid(String value) {
    return value != null && FORMAT.matcher(value).matches();
  }
}
