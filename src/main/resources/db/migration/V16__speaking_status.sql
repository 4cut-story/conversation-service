-- 대사 읽기(SPEAK) 단계 동안 대화 상태를 SPEAKING으로 둔다. 진행 화면의 "목소리 입히는 중".
ALTER TABLE conversations DROP CONSTRAINT ck_conversations_status;
ALTER TABLE conversations ADD CONSTRAINT ck_conversations_status CHECK (status IN (
    'RECORDING', 'QUEUED', 'TRANSCRIBING', 'SELECTING', 'SCRIPTING', 'DRAWING', 'SPEAKING', 'COMPOSING', 'DONE',
    'GPU_WAITING', 'FAILED', 'NO_SCENE'));
