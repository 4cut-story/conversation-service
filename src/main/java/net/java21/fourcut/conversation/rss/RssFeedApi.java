package net.java21.fourcut.conversation.rss;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** RSS 구독 API 본문 (common contracts/rss-feeds.yaml). */
final class RssFeedApi {

  /** 고를 수 있는 그림체. AUTO는 자동. 대화 만들기와 같다. */
  static final String CONCEPTS =
      "AUTO|DAILY|ANIMALS|WATERCOLOR|PICTURE_BOOK|NEWSPAPER|JOSEON|WUXIA";

  /** 고를 수 있는 사투리. NONE은 사투리 없음. */
  static final String DIALECTS = "NONE|GYEONGSANG|JEOLLA|CHUNGCHEONG|GANGWON|SEOUL";

  private RssFeedApi() {}

  /** 피드 등록. 공개 방식 기본값은 확인 후 공개(false). */
  record CreateRequest(
      @NotBlank @Size(max = 2048) String url,
      Boolean autoPublish,
      @Pattern(regexp = CONCEPTS) String concept,
      @Pattern(regexp = DIALECTS) String dialect) {}

  /** 설정 바꾸기 (전체 교체). */
  record UpdateRequest(
      @NotNull Boolean autoPublish,
      @NotNull Boolean enabled,
      @Pattern(regexp = CONCEPTS) String concept,
      @Pattern(regexp = DIALECTS) String dialect) {}

  record View(
      String id,
      String url,
      String title,
      String siteUrl,
      boolean autoPublish,
      boolean enabled,
      String concept,
      String dialect,
      Instant lastCheckedAt,
      String lastError,
      long made,
      Instant createdAt,
      List<ItemView> recent) {}

  record ItemView(
      String title,
      String link,
      RssFeedItem.Status status,
      String reason,
      String conversationId,
      Instant createdAt) {}

  record ListView(List<View> items) {}

  /** 저장할 그림체. 자동이면 null. */
  static String concept(String value) {
    return value == null || "AUTO".equals(value) ? null : value;
  }

  /** 저장할 사투리. 없음이면 null. */
  static String dialect(String value) {
    return value == null || "NONE".equals(value) ? null : value;
  }
}
