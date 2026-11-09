-- 배경음 라이브러리. 만화 장면에 어울리는 장소·환경음, 효과음, 배경음악을 미리 모아 둔다.
-- 상업적으로 쓸 수 있는 CC0, CC BY 클립만 넣고 출처·저작자·라이선스를 함께 둔다(CC BY는 저작자 표시가 조건이다).
-- 소리 임베딩(audio_embedding vector(512), CLAP)과 설명 임베딩(description_embedding vector(1024), BGE-M3) 컬럼과
-- HNSW 인덱스는 pgvector가 있을 때만 afterMigrate.sql이 더한다. pgvector가 없는 DB에서도 이 마이그레이션은 실패하지 않는다.
CREATE TABLE background_sounds (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    public_id VARCHAR(21) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(60) NOT NULL,
    description TEXT NOT NULL,
    moods VARCHAR(200) NOT NULL DEFAULT '',
    places VARCHAR(200) NOT NULL DEFAULT '',
    duration_ms INTEGER NOT NULL,
    audio_key VARCHAR(500) NOT NULL,
    source VARCHAR(20) NOT NULL,
    source_id VARCHAR(50) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    original_title VARCHAR(300),
    author VARCHAR(200) NOT NULL,
    license VARCHAR(20) NOT NULL,
    license_url VARCHAR(300) NOT NULL,
    embedding_model VARCHAR(50),
    audio_model VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT background_sounds_pk PRIMARY KEY (id),
    CONSTRAINT uk_background_sounds_public_id UNIQUE (public_id),
    CONSTRAINT uk_background_sounds_source UNIQUE (source, source_id),
    CONSTRAINT ck_background_sounds_kind CHECK (kind IN ('AMBIENCE', 'EFFECT', 'MUSIC')),
    CONSTRAINT ck_background_sounds_license CHECK (license IN ('CC0-1.0', 'CC-BY-2.0', 'CC-BY-2.5',
        'CC-BY-3.0', 'CC-BY-4.0')),
    CONSTRAINT ck_background_sounds_duration CHECK (duration_ms > 0)
);
CREATE INDEX idx_background_sounds_category ON background_sounds (kind, category);
COMMENT ON TABLE background_sounds IS '배경음. 장면에 붙일 장소·환경음, 효과음, 배경음악과 출처·라이선스';
COMMENT ON COLUMN background_sounds.kind IS 'AMBIENCE(장소·환경음), EFFECT(효과음), MUSIC(배경음악)';
COMMENT ON COLUMN background_sounds.description IS '들리는 소리, 장소, 분위기, 어울리는 장면을 쓴 한국어 설명. 설명 임베딩을 만든다';
COMMENT ON COLUMN background_sounds.moods IS '분위기 코드를 쉼표로 이은 값 (예: PEACEFUL,NOSTALGIC)';
COMMENT ON COLUMN background_sounds.places IS '어울리는 장소 낱말을 쉼표로 이은 값 (예: 바닷가,항구)';
COMMENT ON COLUMN background_sounds.audio_key IS '미리 듣기 MP3 객체 키 (최대 30초, 음량 맞춤)';
COMMENT ON COLUMN background_sounds.author IS '저작자 (CC BY 저작자 표시에 쓴다)';
