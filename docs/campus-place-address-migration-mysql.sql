-- 강의실 이외 장소의 주소 컬럼을 기존 campus_places에 추가합니다.
-- 기존 데이터는 변경하지 않으며 주소를 입력할 때까지 NULL로 유지합니다.
-- 운영은 ddl-auto=validate이므로 주소 기능이 포함된 애플리케이션 배포 전에 실행해야 합니다.

SET NAMES utf8mb4;

-- ============================================================================
-- [0] 항상 먼저 단독 실행
-- address_column_count=0일 때만 [1]을 실행합니다.
-- 1이면 이미 적용됐으므로 [1]을 다시 실행하지 않습니다.
-- ============================================================================
SELECT DATABASE() AS selected_database,
       COUNT(*) AS address_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'campus_places'
  AND column_name = 'address';

-- ============================================================================
-- [1] address_column_count=0일 때만 실행
-- ============================================================================
ALTER TABLE campus_places
    ADD COLUMN address VARCHAR(255) NULL
        AFTER one_line_description;

-- ============================================================================
-- [2] 적용 후 확인
-- ============================================================================
SHOW CREATE TABLE campus_places;

SELECT COUNT(*) AS total_count,
       SUM(category = 'LECTURE_BUILDING' AND address IS NOT NULL)
           AS lecture_building_with_address_count,
       SUM(category IS NOT NULL
           AND category <> 'LECTURE_BUILDING'
           AND address IS NULL)
           AS non_lecture_missing_address_count
FROM campus_places;

-- 관리자 API로 수정하기 전 수동 입력이 필요한 경우의 예시입니다.
-- UPDATE campus_places
-- SET address = '충청남도 서산시 해미면 한서1로 46',
--     updated_at = CURRENT_TIMESTAMP(6)
-- WHERE id = 2
--   AND category <> 'LECTURE_BUILDING';
