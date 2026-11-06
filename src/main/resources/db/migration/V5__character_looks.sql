-- 캐릭터 한 명을 컨셉(테마)·표정마다 다른 그림으로 둔다 (REQ-018 개정). 원본: Crowfoot 4cut-mvp characters, character_looks
-- characters는 정체성(이름, 직업, 성별, 나이대, IP-Adapter 세기)만 둔다. 외모 문구·참조 그림·설명 임베딩은 character_looks로 옮긴다.
-- 설명 임베딩 컬럼(description_embedding vector(768))과 HNSW 인덱스는 pgvector가 있을 때만 afterMigrate.sql이 더한다.
CREATE TABLE character_looks (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    public_id VARCHAR(21) NOT NULL,
    character_id BIGINT NOT NULL,
    concept VARCHAR(20) NOT NULL,
    expression VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL',
    look VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    embedding_model VARCHAR(50),
    reference_image_key VARCHAR(500) NOT NULL,
    ip_adapter_embed_key VARCHAR(500),
    ip_adapter_model VARCHAR(100),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT character_looks_pk PRIMARY KEY (id),
    CONSTRAINT uk_character_looks_public_id UNIQUE (public_id),
    CONSTRAINT uk_character_looks_variant UNIQUE (character_id, concept, expression),
    CONSTRAINT fk_character_looks_character FOREIGN KEY (character_id) REFERENCES characters (id),
    CONSTRAINT ck_character_looks_concept CHECK (concept IN ('DAILY', 'ANIMALS', 'WATERCOLOR',
        'PICTURE_BOOK', 'NEWSPAPER', 'JOSEON', 'WUXIA')),
    CONSTRAINT ck_character_looks_expression CHECK (expression IN ('NEUTRAL', 'HAPPY', 'SAD', 'ANGRY',
        'SURPRISED', 'EMBARRASSED'))
);
CREATE INDEX idx_character_looks_concept ON character_looks (concept, expression);
COMMENT ON TABLE character_looks IS '캐릭터 모습. 캐릭터 한 명의 컨셉·표정별 외모 문구와 참조 그림';
COMMENT ON COLUMN character_looks.look IS 'SDXL 프롬프트에 넣는 영어 외모 문구 (그 컨셉의 옷차림)';
COMMENT ON COLUMN character_looks.description IS '임베딩을 만드는 검색용 설명';
COMMENT ON COLUMN character_looks.ip_adapter_embed_key IS '워커가 계산해 올린 IP-Adapter embedding ([1,1,257,1280] fp16 safetensors) 객체 키';

-- 옛 캐릭터(그림 한 장)는 그 컨셉(없으면 DAILY)의 기본 표정 모습으로 옮긴다. 모습 공개 ID는 캐릭터 공개 ID를 그대로 쓴다.
INSERT INTO character_looks (public_id, character_id, concept, expression, look, description, embedding_model,
    reference_image_key, ip_adapter_embed_key, ip_adapter_model)
SELECT public_id, id, COALESCE(concept, 'DAILY'), 'NEUTRAL', look, description, NULL,
    reference_image_key, ip_adapter_embed_key, ip_adapter_model
FROM characters;

DROP INDEX IF EXISTS idx_characters_description_embedding;
ALTER TABLE characters DROP COLUMN IF EXISTS description_embedding;
ALTER TABLE characters DROP CONSTRAINT IF EXISTS ck_characters_concept;
ALTER TABLE characters
    DROP COLUMN concept,
    DROP COLUMN look,
    DROP COLUMN embedding_model,
    DROP COLUMN reference_image_key,
    DROP COLUMN ip_adapter_embed_key,
    DROP COLUMN ip_adapter_model,
    ADD COLUMN job VARCHAR(50),
    ADD COLUMN gender VARCHAR(10),
    ADD COLUMN age_group VARCHAR(10),
    ADD CONSTRAINT ck_characters_gender CHECK (gender IS NULL OR gender IN ('FEMALE', 'MALE')),
    ADD CONSTRAINT ck_characters_age_group CHECK (age_group IS NULL OR age_group IN ('CHILD', 'YOUNG', 'MIDDLE', 'OLD'));
COMMENT ON COLUMN characters.description IS '캐릭터 한 줄 설명';
COMMENT ON COLUMN characters.job IS '직업 (현대 기준 이름, 예: 의사). 컨셉마다 모습의 옷차림이 바뀐다';
