package net.java21.fourcut.conversation.character;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.support.PublicId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐릭터 관리 내부 API (REQ-018). 게이트웨이 밖으로 열지 않고 운영자가 클러스터 안에서 부른다. 캐릭터를 등록하면 첫 모습의 참조 그림(PNG)을 올릴 사전 서명
 * PUT 주소를 주고, 컨셉·표정별 모습을 더 붙일 수 있다. 참조 그림이 올라와야 그림 단계에서 쓴다.
 */
@RestController
@Validated
@RequestMapping("/internal/characters")
class InternalCharacterController {

  static final String CONCEPTS = "DAILY|ANIMALS|WATERCOLOR|PICTURE_BOOK|NEWSPAPER|JOSEON|WUXIA";
  static final String EXPRESSIONS = "NEUTRAL|HAPPY|SAD|ANGRY|SURPRISED|EMBARRASSED";

  private final CharacterLibrary library;

  InternalCharacterController(CharacterLibrary library) {
    this.library = library;
  }

  record RegisterRequest(
      @NotBlank @Size(max = 30) String name,
      @Size(max = 2000) String description,
      @Size(max = 50) String job,
      @Pattern(regexp = "FEMALE|MALE") String gender,
      @Pattern(regexp = "CHILD|YOUNG|MIDDLE|OLD") String ageGroup,
      @DecimalMin("0.0") @DecimalMax("1.0") Double scale,
      @Pattern(regexp = CONCEPTS) String concept,
      @NotBlank @Size(max = 500) String look,
      @Size(max = 2000) String lookDescription) {}

  record LookRequest(
      @NotBlank @Size(max = 500) String look, @Size(max = 2000) String description) {}

  @PostMapping
  ResponseEntity<CharacterLibrary.Registered> register(@Valid @RequestBody RegisterRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            library.register(
                r.name(),
                r.description(),
                r.job(),
                r.gender(),
                r.ageGroup(),
                r.scale(),
                r.concept(),
                r.look(),
                r.lookDescription()));
  }

  @GetMapping
  List<CharacterLibrary.CharacterView> list() {
    return library.list();
  }

  @GetMapping("/{publicId}")
  CharacterLibrary.CharacterView get(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String publicId) {
    return library.get(publicId);
  }

  /** 컨셉·표정 모습을 만들거나 바꾸고, 참조 그림 업로드 주소를 받는다. */
  @PutMapping("/{publicId}/looks/{concept}/{expression}")
  CharacterLibrary.Registered putLook(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String publicId,
      @PathVariable @Pattern(regexp = CONCEPTS) String concept,
      @PathVariable @Pattern(regexp = EXPRESSIONS) String expression,
      @Valid @RequestBody LookRequest r) {
    return library.putLook(publicId, concept, expression, r.look(), r.description());
  }

  /** 모습의 참조 그림을 바꿀 업로드 주소. 예전 IP-Adapter embedding은 다음 그림 때 다시 계산한다. */
  @PostMapping("/looks/{lookId}/reference")
  CharacterLibrary.Registered replaceReference(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String lookId) {
    return library.replaceReference(lookId);
  }

  /** 지우지 않고 끈다. */
  @DeleteMapping("/{publicId}")
  ResponseEntity<Void> deactivate(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String publicId) {
    library.deactivate(publicId);
    return ResponseEntity.noContent().build();
  }

  /** 설명 임베딩이 없는 기본 표정 모습을 다시 임베딩한다. */
  @PostMapping("/reindex")
  Map<String, Integer> reindex() {
    return Map.of("embedded", library.reindex());
  }
}
