-- 테스트 전용 수동 LLM 처리에서 합성 단계의 말풍선 자리 확인을 LLM 작업으로 넣는다(V8). 합성(COMPOSE) 단계도 작업을 가질 수 있게 한다.
ALTER TABLE gpu_jobs DROP CONSTRAINT ck_gpu_jobs_step;
ALTER TABLE gpu_jobs ADD CONSTRAINT ck_gpu_jobs_step CHECK (
    step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW', 'COMPOSE', 'TRANSCRIBE_PART'));
