package net.java21.fourcut.conversation.conversations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConversationRepository extends JpaRepository<Conversation, Long> {

  @Query(
      "select c from Conversation c where c.ownerId = ?1 and (c.createdAt < ?2 or (c.createdAt = ?2"
          + " and c.id < ?3)) order by c.createdAt desc, c.id desc")
  List<Conversation> page(String ownerId, Instant at, long id, Limit limit);

  List<Conversation> findByOwnerId(String ownerId);

  Optional<Conversation> findByPublicId(String publicId);
}
