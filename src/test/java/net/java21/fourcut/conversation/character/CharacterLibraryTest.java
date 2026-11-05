package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterLibraryTest {

  private static final String ADAPTER = "plus-face-sdxl-vit-h";

  private final StoryCharacterRepository repository = mock(StoryCharacterRepository.class);
  private final CharacterVectors vectors = mock(CharacterVectors.class);
  private final TextEmbedder embedder = mock(TextEmbedder.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private CharacterLibrary library;

  private final StoryCharacter haneul =
      new StoryCharacter("하늘", null, "red bob hair, round glasses", "woman", 0.6);
  private final StoryCharacter haesol =
      new StoryCharacter("해솔", null, "bearded man, blue hoodie", "man", 0.7);
  private final StoryCharacter tiger =
      new StoryCharacter("호랑이", "JOSEON", "tiger in hanbok", "tiger", 0.5);

  @BeforeEach
  void setUp() {
    library = library(true);
    when(storage.exists(anyString())).thenReturn(true);
    when(storage.exists(CharacterKeys.embed(haneul.publicId(), ADAPTER))).thenReturn(false);
    when(storage.exists(CharacterKeys.embed(haesol.publicId(), ADAPTER))).thenReturn(false);
    when(repository.findByPublicId(haneul.publicId())).thenReturn(Optional.of(haneul));
    when(repository.findByPublicId(haesol.publicId())).thenReturn(Optional.of(haesol));
    when(vectors.available()).thenReturn(true);
    when(embedder.embed(anyString())).thenReturn(Optional.of(new float[] {1f}));
  }

  private CharacterLibrary library(boolean enabled) {
    return new CharacterLibrary(
        repository,
        vectors,
        embedder,
        storage,
        new CharacterProperties(enabled, 0.35, "gemini-embedding-001", ADAPTER));
  }

  @Test
  @DisplayName("REQ-018: 이름이 정확히 같으면 임베딩 없이 그 캐릭터를 쓴다")
  void byName() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul));
    Map<String, CharacterRef> cast =
        library.match(List.of(new CharacterLibrary.Wanted("A", "하늘", "girl")), "DAILY");
    assertThat(cast.get("A").publicId()).isEqualTo(haneul.publicId());
    assertThat(cast.get("A").distance()).isZero();
    assertThat(cast.get("A").embedReady()).isFalse();
    verify(embedder, never()).embed(anyString());
  }

  @Test
  @DisplayName("REQ-018: 이름이 없으면 가장 가까운 캐릭터를 쓰고, 이미 쓴 캐릭터는 건너뛴다")
  void byVector() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(
                new CharacterVectors.Nearest(haneul.publicId(), 0.1),
                new CharacterVectors.Nearest(haesol.publicId(), 0.2)));
    Map<String, CharacterRef> cast =
        library.match(
            List.of(
                new CharacterLibrary.Wanted("A", "민지", "girl with red hair"),
                new CharacterLibrary.Wanted("B", "도윤", "girl with red bob")),
            "DAILY");
    assertThat(cast.get("A").publicId()).isEqualTo(haneul.publicId());
    assertThat(cast.get("B").publicId()).isEqualTo(haesol.publicId());
  }

  @Test
  @DisplayName("REQ-018: 거리 한도보다 멀면 캐릭터를 쓰지 않는다")
  void tooFar() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(List.of(new CharacterVectors.Nearest(haneul.publicId(), 0.5)));
    assertThat(library.match(List.of(new CharacterLibrary.Wanted("A", "민지", "cat")), "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 다른 컨셉 전용 캐릭터와 참조 그림이 없는 캐릭터는 쓰지 않는다")
  void conceptAndReference() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(tiger, haesol));
    when(storage.exists(haesol.referenceImageKey())).thenReturn(false);
    when(vectors.nearest(any(), any())).thenReturn(List.of());
    assertThat(
            library.match(
                List.of(
                    new CharacterLibrary.Wanted("A", "호랑이", "tiger"),
                    new CharacterLibrary.Wanted("B", "해솔", "man")),
                "DAILY"))
        .isEmpty();
    assertThat(
            library
                .match(List.of(new CharacterLibrary.Wanted("A", "호랑이", "tiger")), "JOSEON")
                .get("A")
                .publicId())
        .isEqualTo(tiger.publicId());
  }

  @Test
  @DisplayName("REQ-018: 워커가 올린 embedding이 있으면 기록하고 다시 쓴다")
  void embedReuse() {
    String key = CharacterKeys.embed(haneul.publicId(), ADAPTER);
    when(storage.exists(key)).thenReturn(true);
    when(repository.findByPublicIdIn(any())).thenReturn(List.of(haneul));
    library.embedsUploaded(List.of(haneul.publicId()));
    assertThat(haneul.embedKeyFor(ADAPTER)).isEqualTo(key);
    assertThat(haneul.embedKeyFor("other")).isNull();

    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul));
    CharacterRef ref =
        library.match(List.of(new CharacterLibrary.Wanted("A", "하늘", "x")), "DAILY").get("A");
    assertThat(ref.embedKey()).isEqualTo(key);

    haneul.referenceChanged();
    assertThat(haneul.embedKeyFor(ADAPTER)).isNull();
  }

  @Test
  @DisplayName("REQ-018: 끄면 찾지 않는다")
  void disabled() {
    assertThat(library(false).match(List.of(new CharacterLibrary.Wanted("A", "하늘", "x")), "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 임베딩 리터럴은 pgvector 형식이다")
  void literal() {
    assertThat(CharacterVectors.literal(new float[] {0.5f, -1f}))
        .isEqualTo("[0.5000000,-1.000000]");
  }
}
