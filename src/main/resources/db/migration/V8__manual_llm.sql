-- 테스트 전용 수동 LLM 처리. 표시한 대화의 LLM 작업은 서버(Claude·Gemini)와 GPU 워커가 가져가지 않고,
-- 대화 주인이 /api/v1/conversations/{id}/llm-jobs로 꺼내 답을 넣는다
ALTER TABLE conversations ADD COLUMN manual_llm BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE gpu_jobs ADD COLUMN manual BOOLEAN NOT NULL DEFAULT FALSE;
