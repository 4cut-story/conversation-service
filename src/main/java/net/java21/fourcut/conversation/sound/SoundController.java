package net.java21.fourcut.conversation.sound;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import net.java21.fourcut.conversation.support.CursorPage;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 배경음 공개 API (읽기 전용). web이 게이트웨이를 거쳐 부르고, 게이트웨이가 로그인을 확인한다. */
@RestController
@Validated
@RequestMapping("/api/v1/sounds")
class SoundController {

  static final String KINDS = "AMBIENCE|EFFECT|MUSIC";
  static final String CODE = "[A-Z_]{1,30}";

  private final SoundLibrary library;

  SoundController(SoundLibrary library) {
    this.library = library;
  }

  @GetMapping
  CursorPage<SoundLibrary.Sound> list(
      @RequestParam(required = false) @Pattern(regexp = KINDS) String kind,
      @RequestParam(required = false) @Pattern(regexp = CODE) String category,
      @RequestParam(required = false) @Pattern(regexp = CODE) String mood,
      @RequestParam(required = false) @Size(max = 50) String q,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return library.list(new SoundLibrary.Filter(kind, category, mood, q), cursor, size);
  }

  @GetMapping("/facets")
  SoundLibrary.Facets facets() {
    return library.facets();
  }

  @GetMapping("/{id}")
  SoundLibrary.Detail get(@PathVariable String id) {
    return library.get(id);
  }

  @GetMapping("/{id}/audio")
  Map<String, String> audio(@PathVariable String id) {
    return Map.of("url", library.audioUrl(id));
  }
}
