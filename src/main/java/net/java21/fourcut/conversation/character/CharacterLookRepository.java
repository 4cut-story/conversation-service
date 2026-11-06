package net.java21.fourcut.conversation.character;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CharacterLookRepository extends JpaRepository<CharacterLook, Long> {

  Optional<CharacterLook> findByPublicId(String publicId);

  List<CharacterLook> findByPublicIdIn(Collection<String> publicIds);

  /** 캐릭터들의 한 컨셉·표정 모습. */
  List<CharacterLook> findByCharacterInAndConceptAndExpression(
      Collection<StoryCharacter> characters, String concept, String expression);

  Optional<CharacterLook> findByCharacterAndConceptAndExpression(
      StoryCharacter character, String concept, String expression);

  List<CharacterLook> findByCharacterOrderByIdAsc(StoryCharacter character);

  /** 캐릭터 공개 ID → 모습 수. */
  @Query("select l.character.publicId, count(l) from CharacterLook l group by l.character.publicId")
  List<Object[]> countByCharacter();
}
