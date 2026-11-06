package net.java21.fourcut.conversation.character;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.java21.fourcut.conversation.error.ApiException;
import net.java21.fourcut.conversation.error.ErrorCode;
import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.support.Cursor;
import net.java21.fourcut.conversation.support.CursorPage;
import net.java21.fourcut.conversation.support.PublicId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐릭터 갤러리 (REQ-018 보기). 로그인한 사람이 등록된 캐릭터를 둘러본다: 목록은 직업·성별·나이대·이름으로 거르고 컨셉(테마) 하나의 기본 표정 그림을 표지로 보여
 * 준다. 상세는 컨셉 × 표정 모습을 모두 준다. 그림은 사전 서명 GET 주소로 준다.
 */
@Service
class CharacterGallery {

  /** 그림 주소 유효 시간. web이 받자마자 바이트를 가져간다. */
  static final Duration IMAGE_TTL = Duration.ofMinutes(5);

  static final String DEFAULT_CONCEPT = "DAILY";

  private final StoryCharacterRepository characters;
  private final CharacterLookRepository looks;
  private final ObjectStorage storage;

  CharacterGallery(
      StoryCharacterRepository characters, CharacterLookRepository looks, ObjectStorage storage) {
    this.characters = characters;
    this.looks = looks;
    this.storage = storage;
  }

  /** 목록 한 칸. coverLookId는 고른 컨셉의 기본 표정 모습 (없으면 null). */
  record Card(
      String id,
      String name,
      String description,
      String job,
      String gender,
      String ageGroup,
      String coverLookId) {}

  /** 상세. looks는 컨셉·표정 순서. */
  record Detail(
      String id,
      String name,
      String description,
      String job,
      String gender,
      String ageGroup,
      List<Look> looks) {}

  record Look(String id, String concept, String expression, String look) {}

  record JobCount(String job, long count) {}

  record Filter(String concept, String job, String gender, String ageGroup, String q) {}

  @Transactional(readOnly = true)
  CursorPage<Card> list(Filter filter, String cursor, Integer size) {
    int n = CursorPage.size(size);
    long after = Cursor.decodeOr(cursor, Cursor.OLDEST_FIRST).id();
    List<StoryCharacter> rows =
        characters.search(
            after,
            text(filter.job()),
            text(filter.gender()),
            text(filter.ageGroup()),
            text(filter.q()).toLowerCase(Locale.ROOT),
            Limit.of(n + 1));
    String concept = filter.concept() == null ? DEFAULT_CONCEPT : filter.concept();
    return CursorPage.of(
        rows,
        n,
        c -> new Cursor(Cursor.OLDEST_FIRST.at(), c.getId()),
        page -> cards(page, concept));
  }

  private List<Card> cards(List<StoryCharacter> page, String concept) {
    if (page.isEmpty()) {
      return List.of();
    }
    Map<String, String> covers =
        looks
            .findByCharacterInAndConceptAndExpression(page, concept, CharacterLook.NEUTRAL)
            .stream()
            .collect(
                Collectors.toMap(
                    l -> l.character().publicId(), CharacterLook::publicId, (a, b) -> a));
    return page.stream()
        .map(
            c ->
                new Card(
                    c.publicId(),
                    c.name(),
                    c.description(),
                    c.job(),
                    c.gender(),
                    c.ageGroup(),
                    covers.get(c.publicId())))
        .toList();
  }

  @Transactional(readOnly = true)
  Detail get(String publicId) {
    StoryCharacter c = find(publicId);
    List<Look> all =
        looks.findByCharacterOrderByIdAsc(c).stream()
            .sorted(
                Comparator.comparing((CharacterLook l) -> order(Order.CONCEPTS, l.concept()))
                    .thenComparing(l -> order(Order.EXPRESSIONS, l.expression())))
            .map(l -> new Look(l.publicId(), l.concept(), l.expression(), l.look()))
            .toList();
    return new Detail(
        c.publicId(), c.name(), c.description(), c.job(), c.gender(), c.ageGroup(), all);
  }

  /** 직업과 캐릭터 수 (많은 순). 갤러리 직업 필터에 쓴다. */
  @Transactional(readOnly = true)
  List<JobCount> jobs() {
    return characters.countByJob().stream()
        .map(row -> new JobCount((String) row[0], ((Number) row[1]).longValue()))
        .toList();
  }

  /** 모습 그림의 사전 서명 GET 주소. 꺼진 캐릭터의 그림은 주지 않는다. */
  @Transactional(readOnly = true)
  String imageUrl(String lookId) {
    CharacterLook l =
        (PublicId.isValid(lookId) ? looks.findByPublicId(lookId) : Optional.<CharacterLook>empty())
            .filter(x -> x.character().active())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "그림을 찾을 수 없어요."));
    return storage.presignGet(l.referenceImageKey(), IMAGE_TTL);
  }

  private StoryCharacter find(String publicId) {
    return (PublicId.isValid(publicId)
            ? characters.findByPublicId(publicId)
            : Optional.<StoryCharacter>empty())
        .filter(StoryCharacter::active)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "캐릭터를 찾을 수 없어요."));
  }

  private static String text(String value) {
    return value == null ? "" : value.strip();
  }

  /** 컨셉·표정 표시 순서 (내부 API 정규식 순서와 같다). */
  private static final class Order {
    static final Map<String, Integer> CONCEPTS =
        index(InternalCharacterController.CONCEPTS.split("\\|"));
    static final Map<String, Integer> EXPRESSIONS =
        index(InternalCharacterController.EXPRESSIONS.split("\\|"));

    private static Map<String, Integer> index(String[] values) {
      return IntStream.range(0, values.length)
          .boxed()
          .collect(Collectors.toMap(i -> values[i], Function.identity()));
    }
  }

  private static int order(Map<String, Integer> order, String value) {
    return order.getOrDefault(value, Integer.MAX_VALUE);
  }
}
