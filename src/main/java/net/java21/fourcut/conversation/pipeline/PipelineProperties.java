package net.java21.fourcut.conversation.pipeline;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 파이프라인 설정.
 *
 * @param leaseTime 작업 임대 시간 (워커는 그 3분의 1마다 heartbeat)
 * @param workerSeenWithin 이 시간 안에 연락한 워커가 없으면 GPU_WAITING으로 보여 준다
 * @param localStepTimeout 서버 단계(합성)가 이 시간을 넘게 RUNNING이면 실패로 보고 다시 한다
 */
@ConfigurationProperties("pipeline")
public record PipelineProperties(
    @DefaultValue("90s") Duration leaseTime,
    @DefaultValue("60s") Duration workerSeenWithin,
    @DefaultValue("5m") Duration localStepTimeout) {}
