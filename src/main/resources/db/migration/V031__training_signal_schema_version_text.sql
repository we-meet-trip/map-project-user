-- =============================================================================
-- V031 — 학습 신호의 schema_version 을 문자열로 받는다
--
-- 신호를 만드는 agent 가 판 표기를 정수(예: 2)에서 "1.0.0" 같은 문자열로 바꾼다.
-- 두 열을 text 로 넓히고, 이미 쌓인 정수 값은 같은 숫자 문자열로 남긴다
-- (지우거나 바꾸어 쓰는 값은 없다).
--
-- 순서: 이 마이그레이션과 '정수·문자열을 모두 받는' 소비 코드가 먼저 배포된
-- 뒤에 agent 가 표기를 바꾼다. 거꾸로 되면 문자열 신호는 경고만 남기고 버려진다.
-- =============================================================================

ALTER TABLE user_service.recommend_jobs
    ALTER COLUMN schema_version TYPE text USING schema_version::text;

ALTER TABLE user_service.recommend_training
    ALTER COLUMN schema_version TYPE text USING schema_version::text;
