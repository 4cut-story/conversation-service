package net.java21.fourcut.conversation.character;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface StoryCharacterRepository extends JpaRepository<StoryCharacter, Long> {

  Optional<StoryCharacter> findByPublicId(String publicId);

  List<StoryCharacter> findByActiveTrueAndNameIn(Collection<String> names);

  boolean existsByName(String name);

  List<StoryCharacter> findAllByOrderByIdAsc();

  /** 갤러리 목록: 켜진 캐릭터를 id 순으로. 빈 문자열 조건은 거르지 않는다. q는 소문자 이름 일부. */
  @Query(
      """
      select c from StoryCharacter c
      where c.active = true and c.id > :after
        and (:job = '' or c.job = :job)
        and (:gender = '' or c.gender = :gender)
        and (:ageGroup = '' or c.ageGroup = :ageGroup)
        and (:q = '' or lower(c.name) like concat('%', :q, '%'))
      order by c.id""")
  List<StoryCharacter> search(
      long after, String job, String gender, String ageGroup, String q, Limit limit);

  /** 켜진 캐릭터의 직업 → 수 (많은 순, 같으면 이름 순). */
  @Query(
      """
      select c.job, count(c) from StoryCharacter c
      where c.active = true and c.job is not null
      group by c.job order by count(c) desc, c.job""")
  List<Object[]> countByJob();
}
