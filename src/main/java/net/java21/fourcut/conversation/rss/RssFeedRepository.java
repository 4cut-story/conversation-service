package net.java21.fourcut.conversation.rss;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface RssFeedRepository extends JpaRepository<RssFeed, Long> {

  List<RssFeed> findByOwnerIdOrderByCreatedAtDescIdDesc(String ownerId);

  Optional<RssFeed> findByPublicId(String publicId);

  boolean existsByOwnerIdAndUrl(String ownerId, String url);

  @Query("select f.id from RssFeed f where f.enabled = true order by f.id")
  List<Long> enabledIds();

  /** 확인 결과만 남긴다. 확인하는 동안 사용자가 바꾼 설정을 덮어쓰지 않는다. */
  @Modifying
  @Transactional
  @Query("update RssFeed f set f.lastCheckedAt = ?2, f.lastError = ?3 where f.id = ?1")
  int checked(Long id, Instant at, String error);
}
