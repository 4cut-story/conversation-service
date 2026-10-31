package net.java21.fourcut.conversation.web;

/** 서비스가 읽는 헤더 이름 (docs api-conventions 7절). 대소문자는 구분하지 않는다. */
public final class HeaderNames {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String TRACE_ID = "X-Trace-Id";
  public static final String WORKER_ID = "X-Worker-Id";

  private HeaderNames() {}
}
