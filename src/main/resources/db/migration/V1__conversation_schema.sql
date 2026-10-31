-- conversation 스키마 첫 버전. 원본: Crowfoot 4cut-mvp v17 (docs 레포 docs/db/4cut-mvp.sql의 conversation 그룹)
-- 이미 v17이 반영된 DB(개발 공용 스키마)에서는 Flyway baseline(1)으로 건너뛴다.
CREATE TABLE conversations (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    source VARCHAR(20) NOT NULL,
    consent_at TIMESTAMPTZ(6) NOT NULL,
    language VARCHAR(2),
    duration_sec INTEGER,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'RECORDING',
    failure_reason VARCHAR(500),
    comic_id UUID,
    elapsed_sec INTEGER,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT conversations_pk PRIMARY KEY (id),
    CONSTRAINT ck_conversations_source CHECK (source IN ('RECORDING', 'UPLOAD')),
    CONSTRAINT ck_conversations_language CHECK (language IS NULL OR language IN ('ko', 'en')),
    CONSTRAINT ck_conversations_duration CHECK (duration_sec IS NULL OR duration_sec BETWEEN 0 AND 3600),
    CONSTRAINT ck_conversations_status CHECK (status IN ('RECORDING', 'QUEUED', 'TRANSCRIBING', 'SELECTING', 'SCRIPTING', 'DRAWING', 'COMPOSING', 'DONE', 'GPU_WAITING', 'FAILED', 'NO_SCENE'))
);
CREATE TABLE pipeline_steps (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    step VARCHAR(20) NOT NULL,
    attempt SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL,
    runtime_job_id VARCHAR(100),
    result_key VARCHAR(500),
    model_info JSON,
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ(6) NOT NULL,
    finished_at TIMESTAMPTZ(6),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pipeline_steps_pk PRIMARY KEY (id),
    CONSTRAINT uk_pipeline_steps_conversation_id_step_attempt UNIQUE (conversation_id, step, attempt),
    CONSTRAINT ck_pipeline_steps_step CHECK (step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW', 'COMPOSE')),
    CONSTRAINT ck_pipeline_steps_attempt CHECK (attempt BETWEEN 1 AND 3),
    CONSTRAINT ck_pipeline_steps_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);
CREATE TABLE gpu_jobs (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL,
    step VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    payload JSON NOT NULL,
    result_key VARCHAR(500),
    lease_count SMALLINT NOT NULL DEFAULT 0,
    lease_token UUID,
    worker_id VARCHAR(50),
    lease_expires_at TIMESTAMPTZ(6),
    deadline_at TIMESTAMPTZ(6),
    enqueued_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at TIMESTAMPTZ(6),
    finished_at TIMESTAMPTZ(6),
    error_code VARCHAR(50),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT gpu_jobs_pk PRIMARY KEY (id),
    CONSTRAINT ck_gpu_jobs_state CHECK (state IN ('QUEUED', 'LEASED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_gpu_jobs_type CHECK (type IN ('TRANSCRIBE', 'LLM', 'IMAGE')),
    CONSTRAINT ck_gpu_jobs_step CHECK (step IN ('TRANSCRIBE', 'EXTRACT', 'SELECT', 'SCRIPT', 'REVIEW', 'DRAW')),
    CONSTRAINT ck_gpu_jobs_lease_count CHECK (lease_count BETWEEN 0 AND 3)
);
CREATE TABLE gpu_workers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    worker_id VARCHAR(50) NOT NULL,
    last_seen_at TIMESTAMPTZ(6) NOT NULL,
    loaded_model VARCHAR(20),
    version VARCHAR(50),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT gpu_workers_pk PRIMARY KEY (id),
    CONSTRAINT uk_gpu_workers_worker_id UNIQUE (worker_id),
    CONSTRAINT ck_gpu_workers_loaded_model CHECK (loaded_model IS NULL OR loaded_model IN ('WHISPER', 'LLM', 'SDXL'))
);
ALTER TABLE pipeline_steps ADD CONSTRAINT fk_pipeline_steps_conversations FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE;
ALTER TABLE gpu_jobs ADD CONSTRAINT fk_gpu_jobs_conversations FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE;
CREATE INDEX idx_conversations_owner_id_created_at ON conversations (owner_id ASC, created_at DESC);
CREATE INDEX idx_gpu_jobs_state_enqueued_at ON gpu_jobs (state ASC, enqueued_at ASC);
CREATE UNIQUE INDEX uk_gpu_jobs_active_conversation ON gpu_jobs (conversation_id ASC) WHERE state IN ('QUEUED', 'LEASED');
CREATE INDEX idx_gpu_jobs_state_lease_expires_at ON gpu_jobs (state ASC, lease_expires_at ASC);
CREATE INDEX idx_gpu_jobs_conversation_id ON gpu_jobs (conversation_id ASC);
