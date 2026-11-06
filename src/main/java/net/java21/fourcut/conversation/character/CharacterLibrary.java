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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 DB (REQ-018). 캐릭터 한 명은 컨셉(테마)·표정마다 모습(외모 문구 + 참조 그림)을 가진다. 콘티 인물마다 만화 컨셉의 기본 표정 모습을 찾는다: 이름이
 * 정확히 같고 외모 첫 마디(성별·나이)가 맞는 캐릭터가 있으면 그 캐릭터, 아니면 외모 문구의 설명 임베딩과 가장 가까운 모습(거리 한도 안). 한 만화에서 캐릭터 하나는
 * 인물 하나에만 쓴다.
 */
@Service
public class CharacterLibrary {

  private static final Logger log = LoggerFactory.getLogger(CharacterLibrary.class);

  /** 참조 그림 업로드 주소 유효 시간. */
  static final Duration UPLOAD_TTL = Duration.ofMinutes(30);

  /** 임베딩 요청 하나에 묶는 글 수. 임베딩 서버(emb.java21.net)가 받는 최대값이 32다. */
  static final int EMBED_BATCH = 32;

  /** 주기 작업 한 번에 채우는 설명 임베딩 수 (32개 묶음 8번, 약 16초). */
  static final int EMBED_PER_TICK = 256;

  private final StoryCharacterRepository repository;
  private final CharacterLookRepository looks;
  private final CharacterVectors vectors;
  private final TextEmbedder embedder;
  private final ObjectStorage storage;
  private final CharacterProperties properties;

  CharacterLibrary(
      StoryCharacterRepository repository,
      CharacterLookRepository looks,
      CharacterVectors vectors,
      TextEmbedder embedder,
      ObjectStorage storage,
      CharacterProperties properties) {
    this.repository = repository;
    this.looks = looks;
    this.vectors = vectors;
    this.embedder = embedder;
    this.storage = storage;
    this.properties = properties;
  }

  /** 콘티 인물 하나. key는 화자 표시(A, B…), look은 그림용 영어 외모. */
  public record Wanted(String key, String name, String look) {}

  /**
   * 인물 key → 캐릭터 모습. 찾지 못한 인물은 빠진다. 참조 그림이 아직 올라오지 않은 모습은 쓰지 않는다.
   *
   * @param concept 만화 컨셉 이름 (DAILY 등)
   */
  @Transactional
  public Map<String, CharacterRef> match(List<Wanted> wanted, String concept) {
    if (!properties.enabled() || wanted.isEmpty()) {
      return Map.of();
    }
    String c = concept == null ? "DAILY" : concept;
    Map<String, CharacterRef> found = new LinkedHashMap<>();
    Set<String> used = new HashSet<>();
    Map<String, CharacterLook> byName = new HashMap<>();
    List<String> names = wanted.stream().map(Wanted::name).filter(n -> n != null).toList();
    if (!names.isEmpty()) {
      List<StoryCharacter> named = repository.findByActiveTrueAndNameIn(names);
      if (!named.isEmpty()) {
        looks
            .findByCharacterInAndConceptAndExpression(named, c, CharacterLook.NEUTRAL)
            .forEach(l -> byName.put(l.character().name(), l));
      }
    }
    List<Wanted> rest = new ArrayList<>();
    for (Wanted w : wanted) {
      CharacterLook l = w.name() == null ? null : byName.get(w.name().strip());
      // 이름이 같아도 외모 첫 마디(성별·나이)가 어긋나면 쓰지 않는다. 콘티가 할머니에게 "유진" 같은 흔한 가명을 붙이면
      // 같은 이름의 젊은 캐릭터가 붙었다(funny-03)
      if (l != null
          && !used.contains(l.character().publicId())
          && LookHead.of(w.look()).accepts(LookHead.of(l.look()))
          && hasReference(l)) {
        used.add(l.character().publicId());
        found.put(w.key(), ref(l, 0));
      } else {
        rest.add(w);
      }
    }
    if (!rest.isEmpty() && vectors.available()) {
      for (Wanted w : rest) {
        nearest(w.look(), c, used).ifPresent(r -> found.put(w.key(), r));
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
    LookHead wanted = LookHead.of(look);
    for (CharacterVectors.Nearest n : vectors.nearest(query.get(), concept)) {
      if (n.distance() > properties.maxDistance()) {
        return Optional.empty();
      }
      if (!wanted.accepts(LookHead.of(n.look()))) {
        continue;
      }
      Optional<CharacterLook> l = looks.findByPublicId(n.publicId());
      if (l.isPresent()
          && !used.contains(l.get().character().publicId())
          && hasReference(l.get())) {
        used.add(l.get().character().publicId());
        return Optional.of(ref(l.get(), n.distance()));
      }
    }
    return Optional.empty();
  }

  private boolean hasReference(CharacterLook l) {
    return storage.exists(l.referenceImageKey());
  }

  /** embedding이 DB에 없더라도 워커가 이미 올려 두었으면 그것을 쓴다. */
  private CharacterRef ref(CharacterLook l, double distance) {
    String adapter = properties.ipAdapter();
    String target = CharacterKeys.embed(l.character().publicId(), l.publicId(), adapter);
    String ready = l.embedKeyFor(adapter);
    if (ready == null && storage.exists(target)) {
      l.embedReady(adapter, target);
      ready = target;
    }
    return new CharacterRef(
        l.publicId(),
        l.character().name(),
        l.look(),
        l.character().scale(),
        l.referenceImageKey(),
        ready,
        target,
        distance);
  }

  /**
   * 워커가 그림 작업에서 계산해 올린 embedding을 기록한다. 올라오지 않은 것은 다음 그림 때 다시 계산한다.
   *
   * @param lookIds 모습 공개 ID ({@link CharacterRef#publicId()})
   */
  @Transactional
  public void embedsUploaded(Collection<String> lookIds) {
    if (lookIds.isEmpty()) {
      return;
    }
    String adapter = properties.ipAdapter();
    for (CharacterLook l : looks.findByPublicIdIn(lookIds)) {
      String key = CharacterKeys.embed(l.character().publicId(), l.publicId(), adapter);
      if (storage.exists(key)) {
        l.embedReady(adapter, key);
      }
    }
  }

  // ===== 등록 (내부 API) =====

  /** 등록 결과: 캐릭터, 모습, 그 모습의 참조 그림(PNG)을 올릴 사전 서명 PUT 주소. */
  record Registered(CharacterView character, LookView look, String referenceUploadUrl) {}

  /** 내부 API 캐릭터 응답. looks는 한 캐릭터를 볼 때만 채운다. */
  record CharacterView(
      String publicId,
      String name,
      String description,
      String job,
      String gender,
      String ageGroup,
      double scale,
      boolean active,
      long lookCount,
      List<LookView> looks) {}

  /** 내부 API 모습 응답. */
  record LookView(
      String publicId,
      String concept,
      String expression,
      String look,
      String description,
      boolean referenceUploaded,
      boolean embedReady,
      String embeddingModel) {}

  /** 새 캐릭터와 첫 모습(그 컨셉, 기본 표정). */
  @Transactional
  Registered register(
      String name,
      String description,
      String job,
      String gender,
      String ageGroup,
      Double scale,
      String concept,
      String look,
      String lookDescription) {
    String trimmed = name.strip();
    if (repository.existsByName(trimmed)) {
      throw new ApiException(ErrorCode.CONFLICT, "같은 이름의 캐릭터가 있어요.");
    }
    String summary = blank(description) ? look.strip() : description.strip();
    StoryCharacter c =
        repository.save(
            new StoryCharacter(
                trimmed, summary, job, gender, ageGroup, scale == null ? 0.6 : scale));
    CharacterLook l =
        saveLook(
            c,
            concept == null ? "DAILY" : concept,
            CharacterLook.NEUTRAL,
            look,
            blank(lookDescription) ? summary : lookDescription);
    return new Registered(view(c, 1, null), lookView(l), uploadUrl(l));
  }

  /** 캐릭터의 한 컨셉·표정 모습을 만들거나 바꾼다. 바꾸면 참조 그림을 새로 올려야 하고 예전 embedding은 버린다. */
  @Transactional
  Registered putLook(
      String characterId, String concept, String expression, String look, String description) {
    StoryCharacter c = find(characterId);
    String e = expression == null ? CharacterLook.NEUTRAL : expression;
    CharacterLook l = saveLook(c, concept, e, look, blank(description) ? look : description);
    return new Registered(view(c, 0, null), lookView(l), uploadUrl(l));
  }

  private CharacterLook saveLook(
      StoryCharacter c, String concept, String expression, String look, String description) {
    CharacterLook l =
        looks
            .findByCharacterAndConceptAndExpression(c, concept, expression)
            .map(
                existing -> {
                  existing.describe(look.strip(), description.strip());
                  existing.referenceChanged();
                  return existing;
                })
            .orElseGet(
                () ->
                    looks.save(
                        new CharacterLook(
                            c, concept, expression, look.strip(), description.strip())));
    if (CharacterLook.NEUTRAL.equals(expression)) {
      embedDescription(l);
    }
    return l;
  }

  /** 참조 그림을 새로 올릴 주소. 예전 embedding은 버린다. */
  @Transactional
  Registered replaceReference(String lookId) {
    CharacterLook l = looks.findByPublicId(lookId).orElseThrow(ApiException::notFound);
    l.referenceChanged();
    return new Registered(view(l.character(), 0, null), lookView(l), uploadUrl(l));
  }

  @Transactional
  void deactivate(String publicId) {
    find(publicId).deactivate();
  }

  @Transactional(readOnly = true)
  List<CharacterView> list() {
    Map<String, Long> counts = new HashMap<>();
    for (Object[] row : looks.countByCharacter()) {
      counts.put((String) row[0], (Long) row[1]);
    }
    return repository.findAllByOrderByIdAsc().stream()
        .map(c -> view(c, counts.getOrDefault(c.publicId(), 0L), null))
        .toList();
  }

  /** 캐릭터 하나와 모든 모습. */
  @Transactional(readOnly = true)
  CharacterView get(String publicId) {
    StoryCharacter c = find(publicId);
    List<LookView> all = looks.findByCharacterOrderByIdAsc(c).stream().map(this::lookView).toList();
    return view(c, all.size(), all);
  }

  /**
   * 설명 임베딩이 없거나 다른 모델로 만든 기본 표정 모습을 limit개까지 임베딩한다 (서버가 늦게 켜졌거나 실패했거나 모델을 바꿨을 때). 임베딩 서버가 응답하지 않으면
   * 그 자리에서 멈춘다. 처리한 수.
   */
  @Transactional
  int reindex(int limit) {
    if (!vectors.available()) {
      return 0;
    }
    List<CharacterLook> todo =
        vectors.missing(embedder.model(), limit).stream()
            .map(looks::findByPublicId)
            .flatMap(Optional::stream)
            .toList();
    int done = 0;
    for (int from = 0; from < todo.size(); from += EMBED_BATCH) {
      List<CharacterLook> batch = todo.subList(from, Math.min(from + EMBED_BATCH, todo.size()));
      List<float[]> v = embedder.embedAll(batch.stream().map(CharacterLook::description).toList());
      if (v.size() != batch.size()) {
        break;
      }
      for (int i = 0; i < batch.size(); i++) {
        vectors.save(batch.get(i).publicId(), v.get(i), embedder.model());
        batch.get(i).embedded(embedder.model());
      }
      done += batch.size();
    }
    return done;
  }

  /** 주기적으로 빠진 설명 임베딩을 조금씩 채운다. 등록 스크립트가 임베딩 없이 넣은 모습도 여기서 채워진다. */
  @Scheduled(
      fixedDelayString = "${characters.embed-interval:5s}",
      initialDelayString = "${characters.embed-initial-delay:30s}")
  @Transactional
  public void fillMissingEmbeddings() {
    if (!properties.enabled()) {
      return;
    }
    int done = reindex(EMBED_PER_TICK);
    if (done > 0) {
      log.info("캐릭터 설명 임베딩 {}개를 채웠다", done);
    }
  }

  private boolean embedDescription(CharacterLook l) {
    if (!vectors.available()) {
      return false;
    }
    Optional<float[]> v = embedder.embed(l.description());
    v.ifPresent(
        e -> {
          vectors.save(l.publicId(), e, embedder.model());
          l.embedded(embedder.model());
        });
    return v.isPresent();
  }

  private String uploadUrl(CharacterLook l) {
    return storage.presignPut(l.referenceImageKey(), "image/png", UPLOAD_TTL);
  }

  private StoryCharacter find(String publicId) {
    return repository.findByPublicId(publicId).orElseThrow(ApiException::notFound);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private CharacterView view(StoryCharacter c, long lookCount, List<LookView> all) {
    return new CharacterView(
        c.publicId(),
        c.name(),
        c.description(),
        c.job(),
        c.gender(),
        c.ageGroup(),
        c.scale(),
        c.active(),
        lookCount,
        all);
  }

  private LookView lookView(CharacterLook l) {
    return new LookView(
        l.publicId(),
        l.concept(),
        l.expression(),
        l.look(),
        l.description(),
        storage.exists(l.referenceImageKey()),
        l.embedKeyFor(properties.ipAdapter()) != null,
        l.embeddingModel());
  }
}
