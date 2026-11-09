-- 기사로 만들기: 대화 출처에 ARTICLE을 더하고, 기사 원문 주소·제목·사이트를 남긴다(만화 게시물에 출처 링크로 보인다).
ALTER TABLE conversations DROP CONSTRAINT ck_conversations_source;
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_source CHECK (source IN ('RECORDING', 'UPLOAD', 'ARTICLE'));
ALTER TABLE conversations ADD COLUMN source_url VARCHAR(2048);
ALTER TABLE conversations ADD COLUMN source_title VARCHAR(200);
ALTER TABLE conversations ADD COLUMN source_site VARCHAR(100);
