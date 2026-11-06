package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterGalleryTest {

  private final StoryCharacterRepository characters = mock(StoryCharacterRepository.class);
  private final CharacterLookRepository looks = mock(CharacterLookRepository.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private final CharacterGallery gallery = new CharacterGallery(characters, looks, storage);

  private final StoryCharacter haneul =
      new StoryCharacter("하늘", "woman", "의사", "FEMALE", "YOUNG", 0.6);

  @Test
  @DisplayName("REQ-018: 목록은 기본으로 일상 컨셉의 기본 표정 그림을 표지로 쓰고, 조건 앞뒤 공백을 지운다")
  void list() {
    CharacterLook cover = new CharacterLook(haneul, "DAILY", "NEUTRAL", "look", "d");
    when(characters.search(eq(0L), eq("의사"), eq(""), eq(""), eq("하"), any()))
        .thenReturn(List.of(haneul));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("DAILY"), eq("NEUTRAL")))
        .thenReturn(List.of(cover));
    var page = gallery.list(new CharacterGallery.Filter(null, " 의사 ", null, null, "하"), null, null);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).coverLookId()).isEqualTo(cover.publicId());
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  @DisplayName("REQ-018: 맞는 캐릭터가 없으면 빈 목록")
  void empty() {
    when(characters.search(anyLong(), any(), any(), any(), any(), any())).thenReturn(List.of());
    var page = gallery.list(new CharacterGallery.Filter("WUXIA", null, null, null, null), null, 10);
    assertThat(page.items()).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 상세는 컨셉 순서, 그 안에서 표정 순서로 모습을 준다")
  void detail() {
    CharacterLook sad = new CharacterLook(haneul, "DAILY", "SAD", "s", "d");
    CharacterLook wuxia = new CharacterLook(haneul, "WUXIA", "NEUTRAL", "w", "d");
    CharacterLook neutral = new CharacterLook(haneul, "DAILY", "NEUTRAL", "n", "d");
    when(characters.findByPublicId(haneul.publicId())).thenReturn(Optional.of(haneul));
    when(looks.findByCharacterOrderByIdAsc(haneul)).thenReturn(List.of(wuxia, sad, neutral));
    var detail = gallery.get(haneul.publicId());
    assertThat(detail.looks())
        .extracting(CharacterGallery.Look::look)
        .containsExactly("n", "s", "w");
  }

  @Test
  @DisplayName("REQ-018: 꺼진 캐릭터와 형식이 틀린 ID는 찾을 수 없다")
  void notFound() {
    StoryCharacter off = new StoryCharacter("꺼짐", "x", null, null, null, 0.6);
    off.deactivate();
    when(characters.findByPublicId(off.publicId())).thenReturn(Optional.of(off));
    CharacterLook look = new CharacterLook(off, "DAILY", "NEUTRAL", "n", "d");
    when(looks.findByPublicId(look.publicId())).thenReturn(Optional.of(look));
    assertThatThrownBy(() -> gallery.get(off.publicId())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> gallery.get("bad")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> gallery.imageUrl(look.publicId())).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> gallery.imageUrl("bad")).isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("REQ-018: 그림은 사전 서명 주소로, 직업은 수와 함께 준다")
  void imageAndJobs() {
    CharacterLook look = new CharacterLook(haneul, "DAILY", "NEUTRAL", "n", "d");
    when(looks.findByPublicId(look.publicId())).thenReturn(Optional.of(look));
    when(storage.presignGet(look.referenceImageKey(), CharacterGallery.IMAGE_TTL))
        .thenReturn("http://minio/x");
    assertThat(gallery.imageUrl(look.publicId())).isEqualTo("http://minio/x");
    when(characters.countByJob()).thenReturn(List.<Object[]>of(new Object[] {"의사", 3L}));
    assertThat(gallery.jobs()).containsExactly(new CharacterGallery.JobCount("의사", 3));
  }
}
