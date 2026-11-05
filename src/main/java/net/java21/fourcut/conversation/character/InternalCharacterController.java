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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐릭터 관리 내부 API (REQ-018). 게이트웨이 밖으로 열지 않고 운영자가 클러스터 안에서 부른다. 등록하면 참조 그림(PNG)을 올릴 사전 서명 PUT 주소를 준다.
 * 참조 그림이 올라와야 그림 단계에서 쓴다.
 */
@RestController
@Validated
@RequestMapping("/internal/characters")
class InternalCharacterController {

  private final CharacterLibrary library;

  InternalCharacterController(CharacterLibrary library) {
    this.library = library;
  }

  record RegisterRequest(
      @NotBlank @Size(max = 30) String name,
      @Pattern(regexp = "DAILY|ANIMALS|WATERCOLOR|PICTURE_BOOK|NEWSPAPER|JOSEON|WUXIA")
          String concept,
      @NotBlank @Size(max = 500) String look,
      @Size(max = 2000) String description,
      @DecimalMin("0.0") @DecimalMax("1.0") Double scale) {}

  @PostMapping
  ResponseEntity<CharacterLibrary.Registered> register(@Valid @RequestBody RegisterRequest r) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(library.register(r.name(), r.concept(), r.look(), r.description(), r.scale()));
  }

  @GetMapping
  List<CharacterLibrary.CharacterView> list() {
    return library.list();
  }

  /** 참조 그림을 바꿀 업로드 주소. 예전 IP-Adapter embedding은 다음 그림 때 다시 계산한다. */
  @PostMapping("/{publicId}/reference")
  CharacterLibrary.Registered replaceReference(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String publicId) {
    return library.replaceReference(publicId);
  }

  /** 지우지 않고 끈다. */
  @DeleteMapping("/{publicId}")
  ResponseEntity<Void> deactivate(
      @PathVariable @Pattern(regexp = PublicId.PATTERN) String publicId) {
    library.deactivate(publicId);
    return ResponseEntity.noContent().build();
  }

  /** 설명 임베딩이 없는 캐릭터를 다시 임베딩한다. */
  @PostMapping("/reindex")
  Map<String, Integer> reindex() {
    return Map.of("embedded", library.reindex());
  }
}
