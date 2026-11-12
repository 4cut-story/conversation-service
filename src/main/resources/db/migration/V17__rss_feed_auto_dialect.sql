-- RSS 구독 사투리 자동(AUTO): 기사마다 사투리를 골라 섞는다. 지금까지 구독은 사투리를 고르지 않아 모두 표준어로 만들어졌다.
-- 고르지 않은 구독(NULL)을 자동으로 바꾼다. 사투리 없음을 원하면 구독 설정에서 "없음"을 고른다.
UPDATE rss_feeds SET dialect = 'AUTO' WHERE dialect IS NULL;
COMMENT ON COLUMN rss_feeds.dialect IS '사투리. NULL이면 없음, AUTO면 기사마다 자동, 그 밖은 Dialect 이름';
