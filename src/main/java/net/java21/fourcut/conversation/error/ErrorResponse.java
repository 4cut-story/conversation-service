package net.java21.fourcut.conversation.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 공통 오류 형식 {@code {error: {code, message, traceId, details}}} (docs api-conventions 4절). */
public record ErrorResponse(Body error) {

  public static ErrorResponse of(ErrorCode code, String message, String traceId) {
    return new ErrorResponse(new Body(code.name(), message, traceId, null));
  }

  public static ErrorResponse validation(String traceId, List<FieldError> details) {
    ErrorCode code = ErrorCode.VALIDATION_FAILED;
    return new ErrorResponse(new Body(code.name(), code.message(), traceId, details));
  }

  /** 오류 본문. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Body(String code, String message, String traceId, List<FieldError> details) {}

  /** 필드별 검증 오류. */
  public record FieldError(String field, String reason) {}
}
