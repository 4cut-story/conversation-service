package net.java21.fourcut.conversation.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import net.java21.fourcut.conversation.web.HeaderNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 오류를 공통 형식으로 바꾼다. Spring 기본 오류 본문이나 ProblemDetail을 내보내지 않는다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> api(ApiException ex, HttpServletRequest request) {
    return respond(ex.code(), ex.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> invalid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ErrorResponse.FieldError> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
            .toList();
    return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId(request), details));
  }

  @ExceptionHandler({
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<ErrorResponse> invalidParameter(Exception ex, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId(request), List.of()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> malformed(Exception ex, HttpServletRequest request) {
    return respond(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.message(), request);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> missingHeader(
      MissingRequestHeaderException ex, HttpServletRequest request) {
    if (HeaderNames.USER_ID.equalsIgnoreCase(ex.getHeaderName())) {
      return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.message(), request);
    }
    return ResponseEntity.badRequest().body(ErrorResponse.validation(traceId(request), List.of()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> noResource(Exception ex, HttpServletRequest request) {
    return respond(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message(), request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(Exception ex, HttpServletRequest request) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message(), request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaType(Exception ex, HttpServletRequest request) {
    return respond(
        ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.message(), request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> tooLarge(Exception ex, HttpServletRequest request) {
    return respond(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.message(), request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
    log.error("unexpected error traceId={}", traceId(request), ex);
    return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message(), request);
  }

  private static ResponseEntity<ErrorResponse> respond(
      ErrorCode code, String message, HttpServletRequest request) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
    if (code.status() == HttpStatus.UNAUTHORIZED) {
      builder.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    return builder.body(ErrorResponse.of(code, message, traceId(request)));
  }

  static String traceId(HttpServletRequest request) {
    String header = request.getHeader(HeaderNames.TRACE_ID);
    return header == null ? "" : header;
  }
}
