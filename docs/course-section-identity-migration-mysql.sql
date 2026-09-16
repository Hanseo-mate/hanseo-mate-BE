-- 과거 과목코드 단독 identity를 과목코드 + 분반 조합으로 전환하던 이력용 스크립트입니다.
-- 현재 코드는 학년도 + 학기 + 교육과정 유형 + 과목코드 + 분반 identity를 사용하므로
-- 새 배포에는 이 스크립트를 실행하지 말고 강좌 데이터를 초기화한 뒤 엑셀을 재수입합니다.
-- 대상: docs/course-offering-dedup-migration-mysql.sql 적용이 끝난 MySQL 8.0 DB
-- 주의: 애플리케이션을 중지하고 전체 백업과 복원 검증을 완료한 뒤 한 번만 실행하세요.

SET NAMES utf8mb4;

SET @course_count_before = (SELECT COUNT(*) FROM courses);
SET @offering_count_before = (SELECT COUNT(*) FROM course_offerings);
SET @timetable_course_count_before = (SELECT COUNT(*) FROM timetable_courses);

DELIMITER //
CREATE PROCEDURE assert_course_section_identity_ready()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database before running the course section migration';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'courses'
          AND column_name IN ('master_key', 'course_code', 'section_no')
    ) <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'courses.master_key/course_code/section_no schema is not ready';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'courses'
          AND index_name = 'uk_course_code'
          AND non_unique = 0
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'uk_course_code is missing; do not rerun or guess the current schema state';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'courses'
          AND index_name = 'ix_course_code'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ix_course_code already exists; migration may already be partially applied';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM courses coded
        JOIN courses existing
          ON existing.id <> coded.id
         AND existing.master_key = LOWER(SHA2(CONCAT(
                'CODE|', UPPER(TRIM(coded.course_code)),
                '|SECTION|', UPPER(TRIM(COALESCE(coded.section_no, '')))
             ), 256))
        WHERE NULLIF(TRIM(coded.course_code), '') IS NOT NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'A generated course-section master key conflicts with another course';
    END IF;
END//
DELIMITER ;

CALL assert_course_section_identity_ready();
DROP PROCEDURE assert_course_section_identity_ready;

START TRANSACTION;

UPDATE courses
SET master_key = LOWER(SHA2(CONCAT(
        'CODE|', UPPER(TRIM(course_code)),
        '|SECTION|', UPPER(TRIM(COALESCE(section_no, '')))
    ), 256))
WHERE NULLIF(TRIM(course_code), '') IS NOT NULL;

COMMIT;

-- 과목코드는 여러 분반에서 반복될 수 있으므로 UNIQUE를 일반 조회 인덱스로 교체합니다.
ALTER TABLE courses
    DROP INDEX uk_course_code,
    ADD INDEX ix_course_code (course_code);

-- 기존 행과 참조는 그대로 유지되어야 합니다.
SELECT
    @course_count_before AS courses_before,
    COUNT(*) AS courses_after
FROM courses;

SELECT
    @offering_count_before AS offerings_before,
    COUNT(*) AS offerings_after
FROM course_offerings;

SELECT
    @timetable_course_count_before AS timetable_courses_before,
    COUNT(*) AS timetable_courses_after
FROM timetable_courses;

SELECT COUNT(*) AS invalid_master_key_count
FROM courses
WHERE NULLIF(TRIM(course_code), '') IS NOT NULL
  AND master_key <> LOWER(SHA2(CONCAT(
        'CODE|', UPPER(TRIM(course_code)),
        '|SECTION|', UPPER(TRIM(COALESCE(section_no, '')))
      ), 256));

SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'courses'
  AND index_name IN ('uk_course_master_key', 'uk_course_code', 'ix_course_code')
ORDER BY index_name, seq_in_index;
