-- 기존 campus_places에 지도 표시용 카테고리, 한 줄 소개, 대표 이미지 URL을 추가합니다.
-- 기존 114개 장소의 값은 임의로 분류하거나 채우지 않습니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 배포 전에 실행해야 합니다.
-- MySQL DDL은 암시적으로 커밋되므로 [0]을 먼저 실행하고 상태를 확인합니다.

SET NAMES utf8mb4;

-- ============================================================================
-- [0] 항상 먼저 단독 실행
-- 세 컬럼이 모두 없을 때만 [1]을 실행합니다. 1~2개이면 중단하고 부분 적용 상태를
-- 확인합니다. 세 컬럼이 모두 있으면 [1]을 재실행하지 않습니다.
-- ============================================================================
SELECT DATABASE() AS selected_database,
       COUNT(*) AS metadata_column_count,
       GROUP_CONCAT(column_name ORDER BY ordinal_position) AS existing_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'campus_places'
  AND column_name IN (
      'category',
      'one_line_description',
      'image_url'
  );

-- ============================================================================
-- [1] metadata_column_count=0일 때만 실행
-- ============================================================================
ALTER TABLE campus_places
    ADD COLUMN category VARCHAR(40) COLLATE utf8mb4_bin NULL
        AFTER place_name_key,
    ADD COLUMN one_line_description VARCHAR(255) NULL
        AFTER category,
    ADD COLUMN image_url VARCHAR(2048) NULL
        AFTER one_line_description,
    ADD CONSTRAINT ck_campus_place_category
        CHECK (
            category IS NULL OR category IN (
                'RESTAURANT',
                'CAFE',
                'LECTURE_BUILDING',
                'CONVENIENCE_FACILITY'
            )
        );

-- ============================================================================
-- [2] 구조와 기존 데이터 보존 확인
-- ============================================================================
SHOW CREATE TABLE campus_places;

-- 기존 좌표 데이터는 114건이어야 합니다. 분류 전에는 unclassified_count도 114입니다.
SELECT COUNT(*) AS total_count,
       SUM(category IS NULL) AS unclassified_count,
       SUM(one_line_description IS NULL) AS missing_description_count,
       SUM(image_url IS NULL) AS missing_image_count
FROM campus_places;

-- 값이 입력된 행은 아래 네 카테고리 중 하나여야 합니다.
SELECT category, COUNT(*) AS place_count
FROM campus_places
GROUP BY category
ORDER BY category;

-- 사용자가 수동으로 값을 입력할 때 사용할 형식 예시입니다.
-- 실제 장소를 확인한 뒤 id 또는 (campus_code, place_name_key)로 한 행씩 수정합니다.
-- UPDATE campus_places
-- SET category = 'LECTURE_BUILDING',
--     one_line_description = '공학 계열 강의와 실습이 진행되는 건물',
--     image_url = 'https://api.example.com/uploads/campus-places/example.jpg',
--     updated_at = CURRENT_TIMESTAMP(6)
-- WHERE id = 83;
