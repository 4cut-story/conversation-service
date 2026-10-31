package net.java21.fourcut.conversation.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** MinIO(S3 API) 접속 정보. 버킷은 하나({@code 4cut})이고 용도는 접두어로 나눈다. */
@ConfigurationProperties("storage")
public record StorageProperties(
    URI endpoint,
    @DefaultValue("us-east-1") String region,
    String accessKey,
    String secretKey,
    @DefaultValue("4cut") String bucket) {}
