package net.java21.fourcut.conversation.character;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.conversation.support.CursorPage;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 캐릭터 갤러리 공개 API (읽기 전용). web이 게이트웨이를 거쳐 부르고, 게이트웨이가 로그인을 확인한다. 등록·수정은 {@link
 * InternalCharacterController}에만 있다.
 */
@RestController
@Validated
@RequestMapping("/api/v1/characters")
class CharacterGalleryController {

  private final CharacterGallery gallery;

  CharacterGalleryController(CharacterGallery gallery) {
    this.gallery = gallery;
  }

  @GetMapping
  CursorPage<CharacterGallery.Card> list(
      @RequestParam(required = false) @Pattern(regexp = InternalCharacterController.CONCEPTS)
          String concept,
      @RequestParam(required = false) @Size(max = 50) String job,
      @RequestParam(required = false) @Pattern(regexp = "FEMALE|MALE") String gender,
      @RequestParam(required = false) @Pattern(regexp = "CHILD|YOUNG|MIDDLE|OLD") String ageGroup,
      @RequestParam(required = false) @Size(max = 30) String q,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return gallery.list(
        new CharacterGallery.Filter(concept, job, gender, ageGroup, q), cursor, size);
  }

  @GetMapping("/jobs")
  List<CharacterGallery.JobCount> jobs() {
    return gallery.jobs();
  }

  @GetMapping("/{id}")
  CharacterGallery.Detail get(@PathVariable String id) {
    return gallery.get(id);
  }

  @GetMapping("/looks/{lookId}/image")
  Map<String, String> image(@PathVariable String lookId) {
    return Map.of("url", gallery.imageUrl(lookId));
  }
}
