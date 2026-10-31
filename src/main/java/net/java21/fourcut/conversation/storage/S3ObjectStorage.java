package net.java21.fourcut.conversation.storage;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO에 S3 API로 붙는다 (path-style, SigV4). */
public class S3ObjectStorage implements ObjectStorage {

  private static final int NOT_FOUND = 404;

  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  public S3ObjectStorage(S3Client client, S3Presigner presigner, String bucket) {
    this.client = client;
    this.presigner = presigner;
    this.bucket = bucket;
  }

  @Override
  public String presignGet(String key, Duration ttl) {
    return presigner
        .presignGetObject(
            r -> r.signatureDuration(ttl).getObjectRequest(g -> g.bucket(bucket).key(key)))
        .url()
        .toString();
  }

  @Override
  public String presignPut(String key, String contentType, Duration ttl) {
    return presigner
        .presignPutObject(
            r ->
                r.signatureDuration(ttl)
                    .putObjectRequest(p -> p.bucket(bucket).key(key).contentType(contentType)))
        .url()
        .toString();
  }

  @Override
  public void put(String key, byte[] body, String contentType) {
    client.putObject(
        r -> r.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(body));
  }

  @Override
  public Optional<byte[]> get(String key) {
    try {
      return Optional.of(client.getObjectAsBytes(r -> r.bucket(bucket).key(key)).asByteArray());
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

  @Override
  public boolean exists(String key) {
    try {
      client.headObject(r -> r.bucket(bucket).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == NOT_FOUND) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public void deletePrefix(String prefix) {
    for (var page : client.listObjectsV2Paginator(r -> r.bucket(bucket).prefix(prefix))) {
      List<ObjectIdentifier> ids =
          page.contents().stream()
              .map(S3Object::key)
              .map(k -> ObjectIdentifier.builder().key(k).build())
              .toList();
      if (!ids.isEmpty()) {
        client.deleteObjects(
            DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(ids).quiet(true).build())
                .build());
      }
    }
  }
}
