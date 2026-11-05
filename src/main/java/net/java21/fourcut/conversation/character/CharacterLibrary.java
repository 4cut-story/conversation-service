package net.java21.fourcut.conversation.character;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 DB (REQ-018). 콘티 인물마다 캐릭터를 찾는다: 이름이 정확히 같으면 그 캐릭터, 아니면 외모 문구의 설명 임베딩과 가장 가까운 캐릭터(거리 한도 안). 한
 * 만화에서 캐릭터 하나는 인물 하나에만 쓴다.
 */
@Service
public class CharacterLibrary {

  private static final Logger log = LoggerFactory.getLogger(CharacterLibrary.class);

  /** 참조 그림 업로드 주소 유효 시간. */
  static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

  private final StoryCharacterRepository repository;
  private final CharacterVectors vectors;
  private final TextEmbedder embedder;
  private final ObjectStorage storage;
  private final CharacterProperties properties;

  CharacterLibrary(
      StoryCharacterRepository repository,
      CharacterVectors vectors,
      TextEmbedder embedder,
      ObjectStorage storage,
      CharacterProperties properties) {
    this.repository = repository;
    this.vectors = vectors;
    this.embedder = embedder;
    this.storage = storage;
    this.properties = properties;
  }

  /** 콘티 인물 하나. key는 화자 표시(A, B…), look은 그림용 영어 외모. */
  public record Wanted(String key, String name, String look) {}

  /**
   * 인물 key → 캐릭터. 찾지 못한 인물은 빠진다. 참조 그림이 아직 올라오지 않은 캐릭터는 쓰지 않는다.
   *
   * @param concept 만화 컨셉 이름 (DAILY 등)
   */
  @Transactional
  public Map<String, CharacterRef> match(List<Wanted> wanted, String concept) {
    if (!properties.enabled() || wanted.isEmpty()) {
      return Map.of();
    }
    Map<String, CharacterRef> found = new LinkedHashMap<>();
    Set<String> used = new HashSet<>();
    Map<String, StoryCharacter> byName = new HashMap<>();
    List<String> names = wanted.stream().map(Wanted::name).filter(n -> n != null).toList();
    if (!names.isEmpty()) {
      repository.findByActiveTrueAndNameIn(names).forEach(c -> byName.put(c.name(), c));
    }
    List<Wanted> rest = new ArrayList<>();
    for (Wanted w : wanted) {
      StoryCharacter c = w.name() == null ? null : byName.get(w.name().strip());
      if (c != null && fits(c, concept) && !used.contains(c.publicId()) && hasReference(c)) {
        used.add(c.publicId());
        found.put(w.key(), ref(c, 0));
      } else {
        rest.add(w);
      }
    }
    if (!rest.isEmpty() && vectors.available()) {
      for (Wanted w : rest) {
        nearest(w.look(), concept, used)
            .ifPresent(
                r -> {
                  used.add(r.publicId());
                  found.put(w.key(), r);
                });
      }
    }
    if (!found.isEmpty()) {
      log.info("콘티 인물에 캐릭터를 맞췄다: {}", found);
    }
    return found;
  }

  private Optional<CharacterRef> nearest(String look, String concept, Set<String> used) {
    Optional<float[]> query = embedder.embed(look);
    if (query.isEmpty()) {
      return Optional.empty();
    }
    for (CharacterVectors.Nearest n : vectors.nearest(query.get(), concept)) {
      if (n.distance() > properties.maxDistance()) {
        return Optional.empty();
      }
      if (used.contains(n.publicId())) {
        continue;
      }
      Optional<StoryCharacter> c = repository.findByPublicId(n.publicId());
      if (c.isPresent() && hasReference(c.get())) {
        return Optional.of(ref(c.get(), n.distance()));
      }
    }
    return Optional.empty();
  }

  private static boolean fits(StoryCharacter c, String concept) {
    return c.concept() == null || c.concept().equals(concept);
  }

  private boolean hasReference(StoryCharacter c) {
    return storage.exists(c.referenceImageKey());
  }

  /** embedding이 DB에 없더라도 워커가 이미 올려 두었으면 그것을 쓴다. */
  private CharacterRef ref(StoryCharacter c, double distance) {
    String adapter = properties.ipAdapter();
    String target = CharacterKeys.embed(c.publicId(), adapter);
    String ready = c.embedKeyFor(adapter);
    if (ready == null && storage.exists(target)) {
      c.embedReady(adapter, target);
      ready = target;
    }
    return new CharacterRef(
        c.publicId(),
        c.name(),
        c.look(),
        c.scale(),
        c.referenceImageKey(),
        ready,
        target,
        distance);
  }

  /** 워커가 그림 작업에서 계산해 올린 embedding을 기록한다. 올라오지 않은 것은 다음 그림 때 다시 계산한다. */
  @Transactional
  public void embedsUploaded(Collection<String> publicIds) {
    if (publicIds.isEmpty()) {
      return;
    }
    String adapter = properties.ipAdapter();
    for (StoryCharacter c : repository.findByPublicIdIn(publicIds)) {
      String key = CharacterKeys.embed(c.publicId(), adapter);
      if (storage.exists(key)) {
        c.embedReady(adapter, key);
      }
    }
  }

  // ===== 등록 (내부 API) =====

  /** 등록 결과: 캐릭터와 참조 그림(PNG)을 올릴 사전 서명 PUT 주소. */
  record Registered(CharacterView character, String referenceUploadUrl) {}

  /** 내부 API 응답. */
  record CharacterView(
      String publicId,
      String name,
      String concept,
      String look,
      String description,
      double scale,
      boolean active,
      boolean referenceUploaded,
      boolean embedReady,
      String embeddingModel) {}

  @Transactional
  Registered register(String name, String concept, String look, String description, Double scale) {
    String trimmed = name.strip();
    if (repository.existsByName(trimmed)) {
      throw new ApiException(ErrorCode.CONFLICT, "같은 이름의 캐릭터가 있어요.");
    }
    StoryCharacter c =
        repository.save(
            new StoryCharacter(
                trimmed,
                concept,
                look.strip(),
                description == null || description.isBlank() ? look.strip() : description.strip(),
                scale == null ? 0.6 : scale));
    embedDescription(c);
    return new Registered(view(c), uploadUrl(c));
  }

  /** 참조 그림을 새로 올릴 주소. 예전 embedding은 버린다. */
  @Transactional
  Registered replaceReference(String publicId) {
    StoryCharacter c = find(publicId);
    c.referenceChanged();
    return new Registered(view(c), uploadUrl(c));
  }

  @Transactional
  void deactivate(String publicId) {
    find(publicId).deactivate();
  }

  @Transactional(readOnly = true)
  List<CharacterView> list() {
    return repository.findAllByOrderByIdAsc().stream().map(this::view).toList();
  }

  /** 설명 임베딩이 없는 캐릭터를 다시 임베딩한다 (Gemini 키를 나중에 넣었거나 실패했을 때). 처리한 수. */
  @Transactional
  int reindex() {
    int done = 0;
    for (String id : vectors.missing()) {
      if (repository.findByPublicId(id).map(this::embedDescription).orElse(false)) {
        done++;
      }
    }
    return done;
  }

  private boolean embedDescription(StoryCharacter c) {
    if (!vectors.available()) {
      return false;
    }
    Optional<float[]> v = embedder.embed(c.description());
    v.ifPresent(e -> vectors.save(c.publicId(), e, embedder.model()));
    return v.isPresent();
  }

  private String uploadUrl(StoryCharacter c) {
    return storage.presignPut(c.referenceImageKey(), "image/png", UPLOAD_TTL);
  }

  private StoryCharacter find(String publicId) {
    return repository.findByPublicId(publicId).orElseThrow(ApiException::notFound);
  }

  private CharacterView view(StoryCharacter c) {
    return new CharacterView(
        c.publicId(),
        c.name(),
        c.concept(),
        c.look(),
        c.description(),
        c.scale(),
        c.active(),
        storage.exists(c.referenceImageKey()),
        c.embedKeyFor(properties.ipAdapter()) != null,
        c.embeddingModel());
  }
}
