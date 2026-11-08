-- 워커가 결과와 함께 보내는 모델 정보(모델·양자화·step·해상도·시드·GPU·컷별 시간)와 워커가 잰 실행 시간을 작업에 남긴다.
-- 만화의 제작 정보(content-service comics.production)를 이것과 단계 기록으로 만든다. 예전 작업은 비어 있다.
ALTER TABLE gpu_jobs ADD COLUMN model_info JSON;
ALTER TABLE gpu_jobs ADD COLUMN duration_ms BIGINT;
