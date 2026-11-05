-- 캐릭터와 참조 그림 (REQ-018). 원본: Crowfoot 4cut-mvp characters (v35)
-- 콘티 인물을 이 표에서 찾아 SDXL IP-Adapter(Plus-Face)에 참조로 넘긴다.
-- 설명 임베딩 컬럼(description_embedding vector(768))과 HNSW 인덱스는 pgvector가 있을 때만
-- afterMigrate.sql이 더한다. pgvector가 없는 DB에서도 이 마이그레이션은 실패하지 않는다.
CREATE TABLE characters (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    public_id VARCHAR(21) NOT NULL,
    name VARCHAR(30) NOT NULL,
    concept VARCHAR(20),
    look VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    embedding_model VARCHAR(50),
    reference_image_key VARCHAR(500) NOT NULL,
    ip_adapter_embed_key VARCHAR(500),
    ip_adapter_model VARCHAR(100),
    ip_adapter_scale NUMERIC(3,2) NOT NULL DEFAULT 0.60,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT characters_pk PRIMARY KEY (id),
    CONSTRAINT uk_characters_public_id UNIQUE (public_id),
    CONSTRAINT uk_characters_name UNIQUE (name),
    CONSTRAINT ck_characters_ip_adapter_scale CHECK (ip_adapter_scale >= 0 AND ip_adapter_scale <= 1),
    CONSTRAINT ck_characters_concept CHECK (concept IS NULL OR concept IN ('DAILY', 'ANIMALS', 'WATERCOLOR',
        'PICTURE_BOOK', 'NEWSPAPER', 'JOSEON', 'WUXIA'))
);
COMMENT ON TABLE characters IS '캐릭터. 만화에 반복해 나오는 캐릭터와 참조 그림';
COMMENT ON COLUMN characters.look IS 'SDXL 프롬프트에 넣는 영어 외모 문구 (옷차림 포함)';
COMMENT ON COLUMN characters.description IS '임베딩을 만드는 검색용 설명';
COMMENT ON COLUMN characters.ip_adapter_embed_key IS '워커가 계산해 올린 IP-Adapter embedding ([1,1,257,1280] fp16 safetensors) 객체 키';
