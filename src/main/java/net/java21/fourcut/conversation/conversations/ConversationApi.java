package net.java21.fourcut.conversation.conversations;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** 대화 API의 요청·응답 본문 (docs specs/001 contracts/public-api.md). */
public final class ConversationApi {

  private ConversationApi() {}

  /**
   * 대화 만들기.
   *
   * @param consent 함께 대화하는 사람들의 녹음 동의. true가 아니면 400 CONSENT_REQUIRED
   * @param language ko, en. 없으면 자동 감지
   * @param concept 만화 컨셉. AUTO이거나 없으면 장면에 맞게 고른다
   */
  public record CreateRequest(
      @NotNull Conversation.Source source,
      Boolean consent,
      @Pattern(regexp = "ko|en") String language,
      @Pattern(regexp = CONCEPTS) String concept) {

    public CreateRequest(Conversation.Source source, Boolean consent, String language) {
      this(source, consent, language, null);
    }

    /** 저장할 컨셉. 자동이면 null. */
    String chosenConcept() {
      return concept == null || "AUTO".equals(concept) ? null : concept;
    }
  }

  /** 고를 수 있는 컨셉. AUTO는 자동. */
  static final String CONCEPTS = "AUTO|DAILY|ANIMALS|WATERCOLOR|PICTURE_BOOK|NEWSPAPER";

  /** 만든 대화. id는 공개 ID다. */
  public record Created(String id, ConversationStatus status) {}

  /** 녹음 조각 올리기 주소 요청. 조각은 0부터 59까지 (1분 조각 기준 최대 1시간). */
  public record ChunkRequest(@NotNull @Min(0) Integer index, @NotBlank String contentType) {}

  public record ChunkUpload(String putUrl, long expiresIn) {}

  public record FinishRequest(@NotNull @Min(1) @Max(1000) Integer chunkCount) {}

  public record Finished(ConversationStatus status) {}

  /**
   * 대화 진행 상태.
   *
   * @param stage 저장된 진행 단계. GPU를 기다리는 동안 status는 GPU_WAITING이 된다
   * @param retries 지금까지 단계를 다시 시도한 횟수
   * @param id 공개 ID
   * @param comicId 완성된 만화 공개 ID (content-service 만화함)
   */
  public record View(
      String id,
      Conversation.Source source,
      ConversationStatus status,
      ConversationStatus stage,
      int progress,
      int retries,
      String failureReason,
      String comicId,
      String language,
      String concept,
      Integer durationSec,
      Instant createdAt) {}
}
