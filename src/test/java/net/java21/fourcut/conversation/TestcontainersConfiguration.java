package net.java21.fourcut.conversation;

import java.net.URI;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 테스트용 PostgreSQL과 MinIO (H2·가짜 저장소 대신 실제 컨테이너, docs architecture 14절). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final String BUCKET = "4cut";

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:17-alpine");
  }

  /** S3 API 호환 저장소 (MinIO 이미지는 공개 레지스트리에서 받을 수 없어 S3Mock을 쓴다). */
  @Bean
  @SuppressWarnings("resource")
  GenericContainer<?> s3mock() {
    return new GenericContainer<>("adobe/s3mock:4.7.0")
        .withExposedPorts(9090)
        .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
        .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCodeMatching(c -> c < 500));
  }

  private static String endpoint(GenericContainer<?> s3) {
    return "http://" + s3.getHost() + ":" + s3.getMappedPort(9090);
  }

  @Bean
  S3Client testS3(GenericContainer<?> s3mock) {
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint(s3mock)))
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .forcePathStyle(true)
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  @Bean
  DynamicPropertyRegistrar storageProperties(GenericContainer<?> s3mock) {
    return registry -> {
      registry.add("storage.endpoint", () -> endpoint(s3mock));
      registry.add("storage.access-key", () -> "test");
      registry.add("storage.secret-key", () -> "test");
      registry.add("storage.bucket", () -> BUCKET);
    };
  }
}
