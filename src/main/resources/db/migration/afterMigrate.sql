-- 마이그레이션이 끝날 때마다 돈다 (Flyway 콜백). 여러 번 돌아도 같다.
-- pgvector가 있으면 character_looks에 설명 임베딩 컬럼과 HNSW 인덱스를 둔다 (REQ-018).
-- 확장이 없고 만들 권한도 없으면 건너뛴다. 그동안 캐릭터는 이름으로만 찾는다.
-- DB 관리자가 나중에 `CREATE EXTENSION vector;`를 실행하면, 서비스를 다시 띄울 때 컬럼이 생긴다.
DO $$
DECLARE
    vector_schema TEXT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector')
        AND EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'vector') THEN
        BEGIN
            CREATE EXTENSION IF NOT EXISTS vector;
        EXCEPTION WHEN insufficient_privilege THEN
            RAISE NOTICE 'pgvector 확장을 만들 권한이 없어 설명 임베딩 검색을 끈다';
        END;
    END IF;
    SELECT n.nspname INTO vector_schema
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'vector';
    IF vector_schema IS NULL THEN
        RETURN;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'character_looks'
            AND column_name = 'description_embedding') THEN
        EXECUTE format('ALTER TABLE character_looks ADD COLUMN description_embedding %I.vector(768)',
            vector_schema);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema() AND indexname = 'idx_character_looks_description_embedding') THEN
        EXECUTE format('CREATE INDEX idx_character_looks_description_embedding ON character_looks'
            || ' USING hnsw (description_embedding %I.vector_cosine_ops) WITH (m = 16, ef_construction = 64)',
            vector_schema);
    END IF;
END $$;
