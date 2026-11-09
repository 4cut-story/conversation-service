package net.java21.fourcut.conversation.article;

/** 기사를 읽지 못했다. message는 사용자에게 그대로 보여도 되는 문장이다. */
public class ArticleException extends RuntimeException {

  /** 실패 이유. */
  public enum Reason {
    /** http(s)가 아니거나, 내부망·예약 주소이거나, 포트가 80·443이 아니다. */
    BLOCKED_URL("열 수 없는 주소예요. 공개된 기사 주소(http, https)를 넣어 주세요."),
    /** 연결 실패, 시간 초과, 4xx·5xx 응답. */
    UNREACHABLE("기사를 불러오지 못했어요. 주소를 확인하거나 잠시 뒤 다시 해 주세요."),
    /** HTML이 아니다. */
    NOT_HTML("웹 페이지가 아니에요. 기사 페이지 주소를 넣어 주세요."),
    /** 본문이 너무 짧다 (유료 기사, 로그인 필요, 목록 페이지 등). */
    NO_BODY("기사 본문을 찾지 못했어요. 유료 기사나 로그인이 필요한 페이지는 만들 수 없어요.");

    private final String message;

    Reason(String message) {
      this.message = message;
    }

    public String message() {
      return message;
    }
  }

  private final Reason reason;

  public ArticleException(Reason reason, String detail) {
    super(reason.message() + (detail == null ? "" : " (" + detail + ")"));
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  /** 사용자에게 보여 줄 문장 (내부 사정 없이). */
  public String userMessage() {
    return reason.message();
  }
}
