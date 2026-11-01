-- 만화 컨셉(그림체·인물 표현). NULL이면 자동(장면에 맞게 고른다). 원본: Crowfoot 4cut-mvp conversations.concept
ALTER TABLE conversations ADD COLUMN concept VARCHAR(20);
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_concept CHECK (
    concept IS NULL OR concept IN ('DAILY', 'ANIMALS', 'WATERCOLOR', 'PICTURE_BOOK', 'NEWSPAPER'));
