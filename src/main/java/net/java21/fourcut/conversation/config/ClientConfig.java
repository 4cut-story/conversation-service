package net.java21.fourcut.conversation.config;

import java.time.Duration;
import net.java21.fourcut.conversation.comic.ComicProperties;
import net.java21.fourcut.conversation.comic.ContentClient;
import net.java21.fourcut.conversation.web.TraceIdForwarder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 다른 서비스 호출 클라이언트. 연결 2초, 응답 10초. */
@Configuration
public class ClientConfig {

  @Bean
  ContentClient contentClient(RestClient.Builder builder, ComicProperties properties) {
    RestClient client =
        builder
            .baseUrl(properties.contentUrl())
            .requestFactory(
                ClientHttpRequestFactoryBuilder.detect()
                    .build(
                        HttpClientSettings.defaults()
                            .withConnectTimeout(Duration.ofSeconds(2))
                            .withReadTimeout(Duration.ofSeconds(10))))
            .requestInterceptor(new TraceIdForwarder())
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
        .build()
        .createClient(ContentClient.class);
  }
}
