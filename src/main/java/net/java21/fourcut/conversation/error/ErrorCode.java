package net.java21.fourcut.conversation.error;

import org.springframework.http.HttpStatus;

/** 오류 코드 (docs api-conventions 5절). message는 사용자에게 그대로 보여도 되는 문장이다. */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않아요."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능을 쓸 권한이 없어요."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),
  CONFLICT(HttpStatus.CONFLICT, "지금 상태에서는 할 수 없어요."),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 커요."),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식이에요."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 오류가 났어요. 잠시 뒤 다시 시도해 주세요."),
  UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "서버 응답에 문제가 있어요. 잠시 뒤 다시 시도해 주세요."),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 잠시 쓸 수 없어요. 잠시 뒤 다시 시도해 주세요."),
  UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "응답이 늦어지고 있어요. 잠시 뒤 다시 시도해 주세요."),
  CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "함께 대화하는 사람들의 녹음 동의를 확인해 주세요."),
  TOO_LONG(HttpStatus.BAD_REQUEST, "대화는 1시간까지만 만화로 만들 수 있어요."),
  UNSUPPORTED_AUDIO(HttpStatus.BAD_REQUEST, "지원하지 않는 음성 형식이에요."),
  AUDIO_MISSING(HttpStatus.BAD_REQUEST, "올라오지 않은 녹음 조각이 있어요. 다시 시도해 주세요."),
  LEASE_LOST(HttpStatus.CONFLICT, "작업 임대가 끝났어요."),
  ;

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
