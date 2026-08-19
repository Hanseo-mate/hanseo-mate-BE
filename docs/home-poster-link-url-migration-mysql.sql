-- 기존 운영 DB의 홈 포스터에 선택 링크 컬럼을 추가하는 증분 DDL입니다.
-- 실행 전 백업하고, 먼저 아래 조회로 컬럼 존재 여부를 확인하세요.
-- 조회 결과가 없을 때만 ALTER TABLE을 실행합니다.

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'home_posters'
  AND column_name = 'link_url';

ALTER TABLE home_posters
    ADD COLUMN link_url VARCHAR(2048) NULL AFTER image_url;
