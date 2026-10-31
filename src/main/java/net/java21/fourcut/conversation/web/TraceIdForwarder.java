package net.java21.fourcut.conversation.web;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** 다른 서비스를 부를 때 지금 요청의 추적 ID를 넘긴다 (docs api-conventions 9절). */
public class TraceIdForwarder implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws java.io.IOException {
    String traceId = MDC.get("traceId");
    if (traceId != null && !request.getHeaders().containsHeader(HeaderNames.TRACE_ID)) {
      request.getHeaders().set(HeaderNames.TRACE_ID, traceId);
    }
    return execution.execute(request, body);
  }
}
