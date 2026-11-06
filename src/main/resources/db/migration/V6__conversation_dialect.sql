-- 말풍선 대사 사투리. NULL이면 사투리 없음. 원본: Crowfoot 4cut-mvp conversations.dialect
ALTER TABLE conversations ADD COLUMN dialect VARCHAR(20);
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_dialect CHECK (
    dialect IS NULL OR dialect IN ('GYEONGSANG', 'JEOLLA', 'CHUNGCHEONG', 'GANGWON', 'SEOUL'));
