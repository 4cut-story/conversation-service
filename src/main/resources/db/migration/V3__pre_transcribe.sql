-- 녹음 중 선행 전사 (001 FR-015, research.md 10절).
-- 구간 전사(TRANSCRIBE_PART)는 낮은 우선순위(priority 1)로 넣어, 다른 대화의 보통 작업이 기다리면 뒤로 밀린다.
-- 구간 전사는 대화의 진행 중 작업 1개 제한에서 빼고, 따로 대화당 1개만 돈다 (녹음이 끝난 뒤 마무리 전사와 겹칠 수 있다).
ALTER TABLE gpu_jobs ADD COLUMN priority SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE gpu_jobs ADD CONSTRAINT ck_gpu_jobs_priority CHECK (priority IN (0, 1));

ALTER TABLE gpu_jobs DROP CONSTRAINT ck_gpu_jobs_step;
ALTER TABLE gpu_jobs ADD CONSTRAINT ck_gpu_jobs_step CHECK (
    step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW', 'TRANSCRIBE_PART'));

DROP INDEX uk_gpu_jobs_active_conversation;
CREATE UNIQUE INDEX uk_gpu_jobs_active_conversation ON gpu_jobs (conversation_id ASC)
    WHERE state IN ('QUEUED', 'LEASED') AND step <> 'TRANSCRIBE_PART';
CREATE UNIQUE INDEX uk_gpu_jobs_active_part ON gpu_jobs (conversation_id ASC)
    WHERE state IN ('QUEUED', 'LEASED') AND step = 'TRANSCRIBE_PART';
