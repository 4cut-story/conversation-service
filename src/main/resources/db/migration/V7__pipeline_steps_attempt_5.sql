-- 콘티(SCRIPT) 단계는 다섯 번까지 다시 쓴다 (#51, PipelineService.SCRIPT_MAX_ATTEMPTS). 시도 번호 제약이 1~3이라
-- 네 번째 시도를 저장하지 못해 결과 보고가 500으로 끝나고 대화가 SCRIPTING에 멈췄다. 원본: Crowfoot 4cut-mvp pipeline_steps.attempt
ALTER TABLE pipeline_steps DROP CONSTRAINT ck_pipeline_steps_attempt;
ALTER TABLE pipeline_steps ADD CONSTRAINT ck_pipeline_steps_attempt CHECK (attempt BETWEEN 1 AND 5);
