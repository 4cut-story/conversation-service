-- 가상 인물 목소리 (대사 읽기, GPT-SoVITS v4 참조 음성). 목록은 서비스에 함께 넣은 voices/voices.json이 원본이고, 서버가 뜰 때 이 표에 맞춘다.
-- 검색 글(한국어 search_text, 영어 search_text_en)의 설명 임베딩(BGE-M3, 1024차원) 컬럼은 pgvector가 있을 때만 afterMigrate.sql이 더한다.
CREATE TABLE voices (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    voice_id VARCHAR(40) NOT NULL,
    name VARCHAR(20) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    age_group VARCHAR(10) NOT NULL,
    dialect VARCHAR(20),
    tone VARCHAR(60) NOT NULL,
    traits VARCHAR(300) NOT NULL DEFAULT '',
    description TEXT NOT NULL,
    search_text TEXT NOT NULL,
    search_text_en TEXT NOT NULL,
    roles VARCHAR(500) NOT NULL DEFAULT '',
    concepts VARCHAR(200) NOT NULL DEFAULT '',
    f0_hz REAL,
    rate REAL,
    embedding_model VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT voices_pk PRIMARY KEY (id),
    CONSTRAINT uk_voices_voice_id UNIQUE (voice_id),
    CONSTRAINT ck_voices_gender CHECK (gender IN ('FEMALE', 'MALE')),
    CONSTRAINT ck_voices_age_group CHECK (age_group IN ('CHILD', 'YOUNG', 'MIDDLE', 'OLD'))
);
CREATE INDEX idx_voices_gender_age ON voices (gender, age_group);
COMMENT ON TABLE voices IS '가상 인물 목소리. 대사를 읽을 참조 음성과 그 목소리를 찾는 검색 글';
COMMENT ON COLUMN voices.voice_id IS '목소리 ID (web public/voices/<voice_id>/, GPU PC 참조 음성 폴더 이름과 같다)';
COMMENT ON COLUMN voices.dialect IS '사투리 (GYEONGSANG, JEOLLA, CHUNGCHEONG, GANGWON, JEJU). NULL이면 표준어';
COMMENT ON COLUMN voices.search_text IS '한국어 검색 글: 목소리 높낮이·빠르기·느낌, 어울리는 역할과 맞지 않는 역할';
COMMENT ON COLUMN voices.search_text_en IS '영어 검색 글. 콘티가 인물을 영어로 쓰기 때문에 따로 둔다';
COMMENT ON COLUMN voices.f0_hz IS '기본 목소리의 잰 평균 높이(Hz)';
COMMENT ON COLUMN voices.rate IS '기본 목소리의 잰 말 빠르기(초당 글자)';
