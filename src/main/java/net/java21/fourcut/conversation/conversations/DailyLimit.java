package net.java21.fourcut.conversation.conversations;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 사람이 하루(한국 시각)에 만들 수 있는 만화 수. 만화 한 편마다 LLM API와 GPU를 쓰므로, 로그인만 하면 끝없이 만들 수 있게 두지 않는다. 녹음을 마치고
 * 처리로 넘어간 대화만 센다. 관리자와 제외 목록(테스트 계정)은 세지 않는다. 0이면 제한하지 않는다.
 */
@Component
class DailyLimit {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final String ADMIN = "ROLE_ADMIN";

  private final ConversationRepository conversations;
  private final Clock clock;
  private final int perDay;
  private final Set<String> exempt;

  DailyLimit(
      ConversationRepository conversations,
      Clock clock,
      @Value("${conversation.daily-limit:10}") int perDay,
      @Value("${conversation.daily-limit-exempt:}") String exempt) {
    this.conversations = conversations;
    this.clock = clock;
    this.perDay = perDay;
    this.exempt =
        Arrays.stream(exempt.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
  }

  /** 오늘 만들 수 있는 수를 다 썼으면 429 DAILY_LIMIT. */
  @Transactional(readOnly = true)
  public void check(String owner, String roles) {
    if (perDay <= 0 || exempt.contains(owner) || isAdmin(roles)) {
      return;
    }
    var since = LocalDate.now(clock.withZone(KST)).atStartOfDay(KST).toInstant();
    if (conversations.countStartedSince(owner, since) >= perDay) {
      throw new ApiException(ErrorCode.DAILY_LIMIT);
    }
  }

  private static boolean isAdmin(String roles) {
    return roles != null
        && Arrays.stream(roles.split(",")).map(String::strip).anyMatch(ADMIN::equals);
  }
}
