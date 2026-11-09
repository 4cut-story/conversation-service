package net.java21.fourcut.conversation.article;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 기사로 만들기 설정.
 *
 * @param timeout 기사 한 번 받기 시간 한도 (연결 포함)
 * @param maxBytes 받을 HTML 크기 한도. 넘는 부분은 버린다
 * @param maxRedirects 따라갈 넘겨주기(3xx) 수
 * @param allowPrivate 테스트 전용. true면 내부망 주소도 연다. 운영에서는 켜지 않는다
 */
@ConfigurationProperties("article")
public record ArticleProperties(
    @DefaultValue("10s") Duration timeout,
    @DefaultValue("3000000") int maxBytes,
    @DefaultValue("5") int maxRedirects,
    @DefaultValue("false") boolean allowPrivate) {}
