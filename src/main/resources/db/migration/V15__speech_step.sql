-- 대사 읽기(라이브 모드 대사 음성): 파이프라인 단계 SPEAK와 GPU 작업 종류 SPEECH를 더한다.
ALTER TABLE gpu_jobs DROP CONSTRAINT ck_gpu_jobs_type;
ALTER TABLE gpu_jobs ADD CONSTRAINT ck_gpu_jobs_type CHECK (type IN ('TRANSCRIBE', 'LLM', 'IMAGE', 'SPEECH'));

ALTER TABLE gpu_jobs DROP CONSTRAINT ck_gpu_jobs_step;
ALTER TABLE gpu_jobs ADD CONSTRAINT ck_gpu_jobs_step CHECK (
    step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW', 'SPEAK', 'COMPOSE', 'TRANSCRIBE_PART'));

ALTER TABLE pipeline_steps DROP CONSTRAINT ck_pipeline_steps_step;
ALTER TABLE pipeline_steps ADD CONSTRAINT ck_pipeline_steps_step CHECK (
    step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW', 'SPEAK', 'COMPOSE'));
