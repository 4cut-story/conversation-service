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

  /** 그 시각 뒤에 만든 대화 중 녹음을 마치고 처리로 넘어간 것의 수. RSS 구독으로 만든 대화는 세지 않는다 (004 FR-061). */
  @Query(
      "select count(c) from Conversation c where c.ownerId = ?1 and c.createdAt >= ?2"
          + " and c.rssFeedId is null"
          + " and c.status <> net.java21.fourcut.conversation.conversations.ConversationStatus.RECORDING")
  long countStartedSince(String ownerId, Instant since);

  Optional<Conversation> findByPublicId(String publicId);
}
