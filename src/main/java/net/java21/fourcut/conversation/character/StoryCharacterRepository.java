package net.java21.fourcut.conversation.character;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StoryCharacterRepository extends JpaRepository<StoryCharacter, Long> {

  Optional<StoryCharacter> findByPublicId(String publicId);

  List<StoryCharacter> findByActiveTrueAndNameIn(Collection<String> names);

  List<StoryCharacter> findByPublicIdIn(Collection<String> publicIds);

  boolean existsByName(String name);

  List<StoryCharacter> findAllByOrderByIdAsc();
}
