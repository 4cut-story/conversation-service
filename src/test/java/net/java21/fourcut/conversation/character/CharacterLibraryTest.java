package net.java21.fourcut.conversation.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
  private static final String NEUTRAL = CharacterLook.NEUTRAL;

  private final StoryCharacterRepository repository = mock(StoryCharacterRepository.class);
  private final CharacterLookRepository looks = mock(CharacterLookRepository.class);
  private final CharacterVectors vectors = mock(CharacterVectors.class);
  private final TextEmbedder embedder = mock(TextEmbedder.class);
  private final ObjectStorage storage = mock(ObjectStorage.class);
  private CharacterLibrary library;

  private final StoryCharacter haneul =
      new StoryCharacter("하늘", "woman", "의사", "FEMALE", "YOUNG", 0.6);
  private final StoryCharacter haesol = new StoryCharacter("해솔", "man", null, "MALE", "OLD", 0.7);
  private final CharacterLook haneulDaily =
      new CharacterLook(haneul, "DAILY", NEUTRAL, "young woman, red bob, white blouse", "doctor");
  private final CharacterLook haneulJoseon =
      new CharacterLook(
          haneul, "JOSEON", NEUTRAL, "woman in hanbok, black head cloth", "physician");
  private final CharacterLook haesolDaily =
      new CharacterLook(haesol, "DAILY", NEUTRAL, "old man, gray beard, blue hoodie", "man");

  @BeforeEach
  void setUp() {
    library = library(true);
    when(storage.exists(anyString())).thenReturn(true);
    for (CharacterLook l : List.of(haneulDaily, haneulJoseon, haesolDaily)) {
      when(storage.exists(embedKey(l))).thenReturn(false);
      when(looks.findByPublicId(l.publicId())).thenReturn(Optional.of(l));
    }
    when(vectors.available()).thenReturn(true);
    when(embedder.embed(anyString())).thenReturn(Optional.of(new float[] {1f}));
  }

  private static String embedKey(CharacterLook l) {
    return CharacterKeys.embed(l.character().publicId(), l.publicId(), ADAPTER);
  }

  private CharacterLibrary library(boolean enabled) {
    return new CharacterLibrary(
        repository,
        looks,
        vectors,
        embedder,
        storage,
        new CharacterProperties(enabled, 0.30, "http://emb", "bge-m3", ADAPTER, 0.02));
  }

  @Test
  @DisplayName("REQ-018: 이름이 정확히 같으면 임베딩 없이 그 캐릭터의 그 컨셉 모습을 쓴다")
  void byName() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("JOSEON"), eq(NEUTRAL)))
        .thenReturn(List.of(haneulJoseon));
    Map<String, CharacterRef> cast =
        library.match(List.of(new CharacterLibrary.Wanted("A", "하늘", "young woman")), "JOSEON");
    assertThat(cast.get("A").publicId()).isEqualTo(haneulJoseon.publicId());
    assertThat(cast.get("A").look()).contains("hanbok");
    assertThat(cast.get("A").distance()).isZero();
    assertThat(cast.get("A").embedReady()).isFalse();
    verify(embedder, never()).embed(anyString());
  }

  @Test
  @DisplayName("REQ-018: 조선·무협 아이 인물에는 캐릭터를 붙이지 않는다(참조 그림의 아이가 어른으로 그려져 있다)")
  void noChildRefsInJoseonWuxia() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    for (String concept : List.of("WUXIA", "JOSEON")) {
      assertThat(
              library.match(
                  List.of(new CharacterLibrary.Wanted("A", null, "little swordsman, blue robe")),
                  concept))
          .isEmpty();
    }
    verify(embedder, never()).embed(anyString());
    assertThat(CharacterLibrary.adultRefsOnly("DAILY")).isFalse();
  }

  @Test
  @DisplayName("REQ-018: 이름이 같아도 외모 첫 마디의 성별·나이가 다르면 이름으로 쓰지 않는다")
  void byNameNeedsHead() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("JOSEON"), eq(NEUTRAL)))
        .thenReturn(List.of(haneulJoseon));
    when(vectors.available()).thenReturn(false);
    Map<String, CharacterRef> cast =
        library.match(
            List.of(new CharacterLibrary.Wanted("A", "하늘", "old man, gray beard")), "JOSEON");
    assertThat(cast).isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 이름이 없으면 가장 가까운 모습을 쓰고, 이미 쓴 캐릭터는 건너뛴다")
  void byVector() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(
                new CharacterVectors.Nearest(haneulDaily.publicId(), 0.1, haneulDaily.look()),
                new CharacterVectors.Nearest(haesolDaily.publicId(), 0.2, haesolDaily.look())));
    Map<String, CharacterRef> cast =
        library.match(
            List.of(
                new CharacterLibrary.Wanted("A", "민지", "young woman with red hair"),
                new CharacterLibrary.Wanted("B", "도윤", "man with red bob")),
            "DAILY");
    assertThat(cast.get("A").publicId()).isEqualTo(haneulDaily.publicId());
    assertThat(cast.get("B").publicId()).isEqualTo(haesolDaily.publicId());
  }

  @Test
  @DisplayName("REQ-018: 가까워도 외모 첫 마디의 성별·나이가 다른 모습은 건너뛴다")
  void skipsOtherHead() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(
                new CharacterVectors.Nearest(haneulDaily.publicId(), 0.05, haneulDaily.look()),
                new CharacterVectors.Nearest(haesolDaily.publicId(), 0.2, haesolDaily.look())));
    Map<String, CharacterRef> cast =
        library.match(
            List.of(new CharacterLibrary.Wanted("A", "민지", "old man, red bob, white coat")),
            "DAILY");
    assertThat(cast.get("A").publicId()).isEqualTo(haesolDaily.publicId());
  }

  @Test
  @DisplayName("REQ-018: 성별을 아는 인물에는 성별을 모르는 모습보다 성별이 맞는 모습을 먼저 쓴다")
  void knownGenderFirst() {
    StoryCharacter nobody = new StoryCharacter("마연", "long hair", null, null, null, 0.6);
    CharacterLook longHair =
        new CharacterLook(nobody, "DAILY", NEUTRAL, "long brown hair, white shirt", "office");
    when(storage.exists(embedKey(longHair))).thenReturn(false);
    when(looks.findByPublicId(longHair.publicId())).thenReturn(Optional.of(longHair));
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(nobody));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("DAILY"), eq(NEUTRAL)))
        .thenReturn(List.of(longHair));
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(
                new CharacterVectors.Nearest(longHair.publicId(), 0.05, longHair.look()),
                new CharacterVectors.Nearest(haesolDaily.publicId(), 0.2, haesolDaily.look())));
    Map<String, CharacterRef> dad =
        library.match(
            List.of(new CharacterLibrary.Wanted("A", "마연", "man, short hair, glasses")), "DAILY");
    assertThat(dad.get("A").publicId()).isEqualTo(haesolDaily.publicId());
    Map<String, CharacterRef> anyone =
        library.match(
            List.of(new CharacterLibrary.Wanted("A", "민지", "person, long hair")), "DAILY");
    assertThat(anyone.get("A").publicId()).isEqualTo(longHair.publicId());
  }

  @Test
  @DisplayName("REQ-018: 성별을 아는 인물에는 가까운 모습이 성별을 모르는 것뿐이면 캐릭터를 쓰지 않는다")
  void knownGenderSkipsUnknown() {
    StoryCharacter nobody = new StoryCharacter("민준", "glasses", null, null, null, 0.6);
    CharacterLook glasses =
        new CharacterLook(nobody, "DAILY", NEUTRAL, "short brown hair, round glasses", "student");
    when(storage.exists(embedKey(glasses))).thenReturn(false);
    when(looks.findByPublicId(glasses.publicId())).thenReturn(Optional.of(glasses));
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(new CharacterVectors.Nearest(glasses.publicId(), 0.05, glasses.look())));
    assertThat(
            library.match(
                List.of(new CharacterLibrary.Wanted("A", "지윤", "young woman, long black hair")),
                "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 거리 한도보다 멀면 캐릭터를 쓰지 않는다")
  void tooFar() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(new CharacterVectors.Nearest(haneulDaily.publicId(), 0.5, haneulDaily.look())));
    assertThat(library.match(List.of(new CharacterLibrary.Wanted("A", "민지", "cat")), "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 그 컨셉 모습이 없거나 참조 그림이 없으면 쓰지 않는다")
  void conceptAndReference() {
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul, haesol));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("WUXIA"), eq(NEUTRAL)))
        .thenReturn(List.of());
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("DAILY"), eq(NEUTRAL)))
        .thenReturn(List.of(haesolDaily));
    when(storage.exists(haesolDaily.referenceImageKey())).thenReturn(false);
    when(vectors.nearest(any(), any())).thenReturn(List.of());
    assertThat(
            library.match(
                List.of(
                    new CharacterLibrary.Wanted("A", "하늘", "woman"),
                    new CharacterLibrary.Wanted("B", "해솔", "man")),
                "WUXIA"))
        .isEmpty();
    assertThat(library.match(List.of(new CharacterLibrary.Wanted("B", "해솔", "man")), "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 워커가 올린 embedding이 있으면 모습마다 기록하고 다시 쓴다")
  void embedReuse() {
    String key = embedKey(haneulDaily);
    when(storage.exists(key)).thenReturn(true);
    when(looks.findByPublicIdIn(any())).thenReturn(List.of(haneulDaily));
    library.embedsUploaded(List.of(haneulDaily.publicId()));
    assertThat(haneulDaily.embedKeyFor(ADAPTER)).isEqualTo(key);
    assertThat(haneulDaily.embedKeyFor("other")).isNull();
    assertThat(haneulJoseon.embedKeyFor(ADAPTER)).isNull();

    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haneul));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("DAILY"), eq(NEUTRAL)))
        .thenReturn(List.of(haneulDaily));
    CharacterRef ref =
        library.match(List.of(new CharacterLibrary.Wanted("A", "하늘", "x")), "DAILY").get("A");
    assertThat(ref.embedKey()).isEqualTo(key);

    haneulDaily.referenceChanged();
    assertThat(haneulDaily.embedKeyFor(ADAPTER)).isNull();
  }

  @Test
  @DisplayName("REQ-018: 모습마다 참조 그림 키가 다르다")
  void keys() {
    assertThat(haneulDaily.referenceImageKey())
        .isEqualTo(
            "characters/"
                + haneul.publicId()
                + "/looks/"
                + haneulDaily.publicId()
                + "/reference.png")
        .isNotEqualTo(haneulJoseon.referenceImageKey());
  }

  @Test
  @DisplayName("REQ-018: 끄면 찾지 않는다")
  void disabled() {
    assertThat(library(false).match(List.of(new CharacterLibrary.Wanted("A", "하늘", "x")), "DAILY"))
        .isEmpty();
  }

  @Test
  @DisplayName("REQ-018: 빠진 설명 임베딩을 32개씩 묶어 채운다")
  void fillMissing() {
    when(embedder.model()).thenReturn("bge-m3");
    when(vectors.missing("bge-m3", CharacterLibrary.EMBED_PER_TICK))
        .thenReturn(
            List.of(haneulDaily.publicId(), haneulJoseon.publicId(), haesolDaily.publicId()));
    when(embedder.embedAll(anyList()))
        .thenAnswer(
            inv -> ((List<?>) inv.getArgument(0)).stream().map(t -> new float[] {1f}).toList());
    library.fillMissingEmbeddings();
    verify(embedder).embedAll(anyList());
    verify(vectors).save(eq(haesolDaily.publicId()), any(), eq("bge-m3"));
    assertThat(haneulDaily.embeddingModel()).isEqualTo("bge-m3");
  }

  @Test
  @DisplayName("REQ-018: 임베딩 서버가 응답하지 않으면 채우지 않고 멈춘다")
  void fillMissingFailure() {
    when(embedder.model()).thenReturn("bge-m3");
    when(vectors.missing("bge-m3", CharacterLibrary.EMBED_PER_TICK))
        .thenReturn(List.of(haneulDaily.publicId()));
    when(embedder.embedAll(anyList())).thenReturn(List.of());
    library.fillMissingEmbeddings();
    verify(vectors, never()).save(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("REQ-018: 끄면 설명 임베딩을 채우지 않는다")
  void fillMissingDisabled() {
    library(false).fillMissingEmbeddings();
    verify(vectors, never()).missing(anyString(), anyInt());
  }

  @Test
  @DisplayName("REQ-018: 임베딩 리터럴은 pgvector 형식이다")
  void literal() {
    assertThat(CharacterVectors.literal(new float[] {0.5f, -1f}))
        .isEqualTo("[0.5000000,-1.000000]");
  }

  @Test
  @DisplayName("REQ-018: 콘티가 말하지 않은 직업 옷 모습은 가까워도 붙이지 않는다")
  void skipsCostume() {
    CharacterLook cop =
        new CharacterLook(
            haneul, "DAILY", NEUTRAL, "young woman, police cap, police uniform", "police");
    when(looks.findByPublicId(cop.publicId())).thenReturn(Optional.of(cop));
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(List.of(new CharacterVectors.Nearest(cop.publicId(), 0.1, cop.look())));
    assertThat(
            library.match(
                List.of(new CharacterLibrary.Wanted("A", null, "young woman, beige knit sweater")),
                "DAILY"))
        .isEmpty();
    assertThat(
            library.match(
                List.of(new CharacterLibrary.Wanted("A", null, "young woman, police uniform")),
                "DAILY"))
        .containsKey("A");
  }

  @Test
  @DisplayName("REQ-018: 역할 옷을 입은 캐릭터가 거리 안에 있으면 더 가까운 평상복 캐릭터보다 먼저 쓴다")
  void prefersCostume() {
    CharacterLook casual =
        new CharacterLook(
            haesol, "DAILY", NEUTRAL, "young man, short black hair, cream sweater", "m");
    CharacterLook cop =
        new CharacterLook(haneul, "DAILY", NEUTRAL, "young man, police cap, police uniform", "p");
    for (CharacterLook l : List.of(casual, cop)) {
      when(looks.findByPublicId(l.publicId())).thenReturn(Optional.of(l));
    }
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of());
    when(vectors.nearest(any(), any()))
        .thenReturn(
            List.of(
                new CharacterVectors.Nearest(casual.publicId(), 0.10, casual.look()),
                new CharacterVectors.Nearest(cop.publicId(), 0.20, cop.look())));
    Map<String, CharacterRef> cast =
        library.match(
            List.of(
                new CharacterLibrary.Wanted(
                    "A",
                    null,
                    "young man, short hair, navy police uniform",
                    "navy police uniform, police cap")),
            "DAILY");
    assertThat(cast.get("A").publicId()).isEqualTo(cop.publicId());
    assertThat(cast.get("A").costumed()).isFalse();
  }

  @Test
  @DisplayName("REQ-018: 역할 옷을 입은 캐릭터가 없으면 가장 가까운 캐릭터에 콘티의 역할 옷을 입힌다(상병이 스웨터 차림으로 나왔다)")
  void dressesCostume() {
    CharacterLook casual =
        new CharacterLook(
            haesol, "DAILY", NEUTRAL, "young man, short black hair, cream sweater", "m");
    when(looks.findByPublicId(casual.publicId())).thenReturn(Optional.of(casual));
    when(repository.findByActiveTrueAndNameIn(any())).thenReturn(List.of(haesol));
    when(looks.findByCharacterInAndConceptAndExpression(any(), eq("DAILY"), eq(NEUTRAL)))
        .thenReturn(List.of(casual));
    when(vectors.nearest(any(), any()))
        .thenReturn(List.of(new CharacterVectors.Nearest(casual.publicId(), 0.1, casual.look())));
    String look = "young man, short black hair, olive green military uniform, combat helmet";
    Map<String, CharacterRef> byVector =
        library.match(List.of(new CharacterLibrary.Wanted("A", null, look)), "DAILY");
    assertThat(byVector.get("A").costume())
        .isEqualTo("olive green military uniform, combat helmet");
    Map<String, CharacterRef> byName =
        library.match(
            List.of(new CharacterLibrary.Wanted("A", "해솔", look, "olive military uniform")),
            "DAILY");
    assertThat(byName.get("A").publicId()).isEqualTo(casual.publicId());
    assertThat(byName.get("A").costume()).isEqualTo("olive military uniform");
    assertThat(
            CharacterLibrary.drawLook(
                byName.get("A").look(), look, true, byName.get("A").costume()))
        .contains("olive military uniform")
        .contains("short black hair")
        .doesNotContain("sweater");
  }

  @Test
  @DisplayName("REQ-018: 거리가 사실상 같은 후보는 대화 시드로 고르고, 차이가 크면 늘 가장 가까운 후보다")
  void tieBreak() {
    var a = new CharacterLibrary.Candidate(null, 0.10);
    var b = new CharacterLibrary.Candidate(null, 0.11);
    var far = new CharacterLibrary.Candidate(null, 0.20);
    List<CharacterLibrary.Candidate> fits = List.of(a, b, far);

    assertThat(CharacterLibrary.tieBreak(fits, 0.02, 0)).containsSame(a);
    assertThat(CharacterLibrary.tieBreak(fits, 0.02, 1)).containsSame(b);
    assertThat(CharacterLibrary.tieBreak(fits, 0.02, 2)).containsSame(a);
    assertThat(
            java.util.stream.LongStream.range(0, 20)
                .mapToObj(seed -> CharacterLibrary.tieBreak(fits, 0.02, seed).orElseThrow())
                .distinct())
        .containsExactlyInAnyOrder(a, b);
    assertThat(CharacterLibrary.tieBreak(List.of(), 0.02, 3)).isEmpty();
  }
}
