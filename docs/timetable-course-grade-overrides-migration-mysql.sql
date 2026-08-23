-- 기존 운영 DB의 시간표 과목에 사용자별 과목명·학점 덮어쓰기 컬럼을 추가합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 기존 DB에 실행하지 않습니다.
-- MySQL DDL은 암시적으로 commit되므로 애플리케이션 쓰기를 중지하고 백업한 뒤 적용합니다.
-- 예상 성적 증분 DDL 적용 후 한 번만 실행합니다.

DROP PROCEDURE IF EXISTS assert_grade_overrides_migration_ready;
DELIMITER //
CREATE PROCEDURE assert_grade_overrides_migration_ready()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database before running the grade overrides migration';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'expected_grade'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Apply the expected grade migration first';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'custom_course_name'
    ) OR EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'custom_credit'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Grade overrides migration is already or partially applied';
    END IF;
END//
DELIMITER ;

CALL assert_grade_overrides_migration_ready();
DROP PROCEDURE assert_grade_overrides_migration_ready;

ALTER TABLE timetable_courses
    ADD COLUMN custom_course_name VARCHAR(255) NULL
        AFTER expected_grade,
    ADD COLUMN custom_credit DECIMAL(8,3) NULL
        AFTER custom_course_name,
    ADD CONSTRAINT ck_timetable_course_custom_name CHECK (
        custom_course_name IS NULL
        OR CHAR_LENGTH(TRIM(custom_course_name)) > 0
    ),
    ADD CONSTRAINT ck_timetable_course_custom_credit CHECK (
        custom_credit IS NULL
        OR (custom_credit >= 0.001 AND custom_credit <= 20.000)
    );

-- 두 컬럼은 nullable이어야 하며 기존 행은 모두 NULL로 시작합니다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'timetable_courses'
  AND column_name IN ('custom_course_name', 'custom_credit')
ORDER BY ordinal_position;

SELECT table_constraint.constraint_name,
       table_constraint.constraint_type,
       table_constraint.enforced,
       check_constraint.check_clause
FROM information_schema.table_constraints table_constraint
JOIN information_schema.check_constraints check_constraint
  ON check_constraint.constraint_schema = table_constraint.constraint_schema
 AND check_constraint.constraint_name = table_constraint.constraint_name
WHERE table_constraint.constraint_schema = DATABASE()
  AND table_constraint.table_name = 'timetable_courses'
  AND table_constraint.constraint_name IN (
      'ck_timetable_course_custom_name',
      'ck_timetable_course_custom_credit'
  )
ORDER BY table_constraint.constraint_name;

SELECT COUNT(*) AS rows_with_existing_overrides
FROM timetable_courses
WHERE custom_course_name IS NOT NULL
   OR custom_credit IS NOT NULL;
