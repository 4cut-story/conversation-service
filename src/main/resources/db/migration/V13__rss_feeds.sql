-- RSS 구독 (docs specs/004-rss-subscription). 사용자가 등록한 피드를 30분마다 확인해 새 기사로 만화를 만든다.
-- 기사마다 기록을 먼저 남기고(유일 키) 만들어, 서버가 다시 떠도 같은 기사로 두 번 만들지 않는다.
CREATE TABLE rss_feeds (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    public_id VARCHAR(21) NOT NULL,
    owner_id VARCHAR(21) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(200) NOT NULL,
    site_url VARCHAR(2048),
    auto_publish BOOLEAN NOT NULL DEFAULT FALSE,
    concept VARCHAR(20),
    dialect VARCHAR(20),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_checked_at TIMESTAMPTZ(6),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT rss_feeds_pk PRIMARY KEY (id),
    CONSTRAINT uk_rss_feeds_public_id UNIQUE (public_id),
    CONSTRAINT uk_rss_feeds_owner_url UNIQUE (owner_id, url)
);

CREATE TABLE rss_feed_items (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    feed_id BIGINT NOT NULL,
    item_key VARCHAR(2048) NOT NULL,
    link VARCHAR(2048) NOT NULL,
    title VARCHAR(300),
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    conversation_id VARCHAR(21),
    embedding BYTEA,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT rss_feed_items_pk PRIMARY KEY (id),
    CONSTRAINT fk_rss_feed_items_feed FOREIGN KEY (feed_id) REFERENCES rss_feeds (id) ON DELETE CASCADE,
    CONSTRAINT uk_rss_feed_items_key UNIQUE (feed_id, item_key),
    CONSTRAINT ck_rss_feed_items_status CHECK (status IN ('MADE', 'SKIPPED', 'SIMILAR', 'FAILED'))
);
CREATE INDEX ix_rss_feed_items_feed_created ON rss_feed_items (feed_id, created_at DESC);
-- embedding: 기사 제목·앞부분의 설명 임베딩(BGE-M3 1024차원 float, 리틀 엔디언). 최근에 만든 기사와 주제가 비슷하면 건너뛸 때 비교한다(FR-068).

-- RSS로 만든 대화: 어느 구독에서 왔는지와 완성되면 바로 공개할지. 하루 만화 수 제한에 세지 않는다.
-- 구독을 지워도 값은 남긴다(외래 키를 두지 않는다). 그래야 지운 뒤에도 RSS로 만든 대화로 남는다.
ALTER TABLE conversations ADD COLUMN rss_feed_id BIGINT;
ALTER TABLE conversations ADD COLUMN auto_publish BOOLEAN NOT NULL DEFAULT FALSE;
