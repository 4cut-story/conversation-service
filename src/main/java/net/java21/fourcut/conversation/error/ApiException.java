package net.java21.fourcut.conversation.error;

/** 공통 오류 형식으로 응답할 도메인 예외. */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ApiException(ErrorCode code) {
    this(code, code.message());
  }

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ApiException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }

  public static ApiException notFound() {
    return new ApiException(ErrorCode.NOT_FOUND);
  }
}
