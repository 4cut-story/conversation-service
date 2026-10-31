package net.java21.fourcut.conversation.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * 객체 저장소 (MinIO). 키는 버킷 안의 경로다. 접두어: {@code audio-raw/}, {@code pipeline-work/}, {@code comics/}.
 */
public interface ObjectStorage {

  /** 사전 서명 GET 주소. */
  String presignGet(String key, Duration ttl);

  /** 사전 서명 PUT 주소. contentType이 있으면 업로드도 같은 Content-Type이어야 한다. */
  String presignPut(String key, String contentType, Duration ttl);

  void put(String key, byte[] body, String contentType);

  /** 객체 내용. 없으면 빈 값. */
  Optional<byte[]> get(String key);

  boolean exists(String key);

  /** 접두어 아래 객체를 모두 지운다. 없어도 성공이다. */
  void deletePrefix(String prefix);
}
