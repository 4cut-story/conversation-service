-- 마이그레이션이 끝날 때마다 돈다 (Flyway 콜백). 여러 번 돌아도 같다.
-- pgvector가 있으면 character_looks에 설명 임베딩 컬럼(BGE-M3, 1024차원)과 HNSW 인덱스를 둔다 (REQ-018).
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
    -- 차원이 다른 옛 컬럼(Gemini 768차원)은 지우고 다시 만든다. 인덱스도 함께 지워지고, 서비스가 임베딩을 다시 채운다
    IF EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('character_looks') AND attname = 'description_embedding'
            AND NOT attisdropped AND atttypmod <> 1024) THEN
        ALTER TABLE character_looks DROP COLUMN description_embedding;
        UPDATE character_looks SET embedding_model = NULL;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'character_looks'
            AND column_name = 'description_embedding') THEN
        EXECUTE format('ALTER TABLE character_looks ADD COLUMN description_embedding %I.vector(1024)',
            vector_schema);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = current_schema() AND indexname = 'idx_character_looks_description_embedding') THEN
        -- 병렬 빌드는 공유 메모리(/dev/shm)를 써서 작은 DB 서버에서 "No space left on device"로 실패한다. 한 프로세스로 짓는다.
        -- 그래도 실패하면 인덱스 없이 띄운다(모습 7,000개는 순차 검색으로도 충분하다). 서비스가 뜨지 못하는 것보다 낫다.
        PERFORM set_config('max_parallel_maintenance_workers', '0', true);
        PERFORM set_config('maintenance_work_mem', '64MB', true);
        BEGIN
            EXECUTE format('CREATE INDEX idx_character_looks_description_embedding ON character_looks'
                || ' USING hnsw (description_embedding %I.vector_cosine_ops) WITH (m = 16, ef_construction = 64)',
                vector_schema);
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING '설명 임베딩 인덱스를 만들지 못해 인덱스 없이 찾는다: %', SQLERRM;
        END;
    END IF;
END $$;
