package net.java21.fourcut.conversation.conversations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  @Query(
      "select c from Conversation c where c.ownerId = ?1 and (c.createdAt < ?2 or (c.createdAt = ?2"
          + " and c.id < ?3)) order by c.createdAt desc, c.id desc")
  List<Conversation> page(UUID ownerId, Instant at, UUID id, Limit limit);

  List<Conversation> findByOwnerId(UUID ownerId);
}
