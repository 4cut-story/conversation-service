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
 * @param preTranscribe 녹음 중 구간 전사(FR-015)를 켠다. 끄면 녹음이 끝난 뒤 한 번에 전사한다
 */
@ConfigurationProperties("pipeline")
public record PipelineProperties(
    @DefaultValue("90s") Duration leaseTime,
    @DefaultValue("60s") Duration workerSeenWithin,
    @DefaultValue("8m") Duration localStepTimeout,
    @DefaultValue("true") boolean preTranscribe) {}
