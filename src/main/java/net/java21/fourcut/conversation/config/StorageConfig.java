package net.java21.fourcut.conversation.config;

import net.java21.fourcut.conversation.storage.ObjectStorage;
import net.java21.fourcut.conversation.storage.S3ObjectStorage;
import net.java21.fourcut.conversation.storage.StorageProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** MinIO 클라이언트. 키는 Secret(MINIO_ACCESS_KEY, MINIO_SECRET_KEY)에서 온다. */
@Configuration
public class StorageConfig {

  @Bean
  ObjectStorage objectStorage(StorageProperties p) {
    StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
    S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
    S3Client client =
        S3Client.builder()
            .endpointOverride(p.endpoint())
            .region(Region.of(p.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(pathStyle)
            .httpClient(UrlConnectionHttpClient.create())
            .build();
    S3Presigner presigner =
        S3Presigner.builder()
            .endpointOverride(p.endpoint())
            .region(Region.of(p.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(pathStyle)
            .build();
    return new S3ObjectStorage(client, presigner, p.bucket());
  }
}
