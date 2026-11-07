package net.java21.fourcut.conversation.character;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.java21.fourcut.conversation.support.PublicId;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPU PC 워커가 캐릭터 참조 그림을 다시 그려 올리는 경로 (REQ-018). 게이트웨이는 {@code /internal/gpu-jobs/**}만 밖에 열고 워커 토큰을
 * 확인한다. 그림체 모습에서 빠진 나이를 넣거나 중복 캐릭터의 옷을 바꿀 때, GPU PC가 새 그림을 그린 뒤 이 경로로 문구를 바꾸고 받은 주소로 그림을 올린다.
 */
@RestController
@Validated
@RequestMapping("/internal/gpu-jobs/character-looks")
class WorkerCharacterController {

  private final CharacterLibrary library;

  WorkerCharacterController(CharacterLibrary library) {
    this.library = library;
  }

  /**
   * 바꿀 모습 문구.
   *
   * @param before 지금 문구(다르면 409). 없으면 확인하지 않는다
   * @param look 새 문구
   */
  record RelookRequest(@Size(max = 500) String before, @NotBlank @Size(max = 500) String look) {}

  /** 모습 문구를 바꾸고 새 참조 그림 업로드 주소를 받는다. 예전 IP-Adapter embedding은 다음 그림 때 다시 계산한다. */
  @PutMapping("/{lookId}")
  CharacterLibrary.Registered relook(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String lookId,
      @Valid @RequestBody RelookRequest r) {
    return library.relook(lookId, r.before(), r.look());
  }
}
