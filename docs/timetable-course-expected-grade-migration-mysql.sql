-- 기존 운영 DB의 시간표 과목에 사용자별 예상 성적 컬럼을 추가합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 기존 DB에 실행하지 않습니다.
-- MySQL DDL은 암시적으로 commit되므로 애플리케이션 쓰기를 중지하고 백업한 뒤 적용합니다.
-- CHECK 제약을 실제로 강제하는 MySQL 8.0.16 이상에서 한 번만 실행합니다.

DROP PROCEDURE IF EXISTS assert_expected_grade_migration_ready;
DELIMITER //
CREATE PROCEDURE assert_expected_grade_migration_ready()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database before running the grade migration';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'course_offering_id'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Apply the course offering dedup migration first';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'expected_grade'
    ) OR EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND constraint_name = 'ck_timetable_course_expected_grade'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Expected grade migration is already or partially applied';
    END IF;
END//
DELIMITER ;

CALL assert_expected_grade_migration_ready();
DROP PROCEDURE assert_expected_grade_migration_ready;

ALTER TABLE timetable_courses
    ADD COLUMN expected_grade VARCHAR(20) NULL
        AFTER course_offering_id,
    ADD CONSTRAINT ck_timetable_course_expected_grade CHECK (
        expected_grade IS NULL
        OR BINARY expected_grade IN (
            BINARY 'A_PLUS',
            BINARY 'A',
            BINARY 'B_PLUS',
            BINARY 'B',
            BINARY 'C_PLUS',
            BINARY 'C',
            BINARY 'D_PLUS',
            BINARY 'D',
            BINARY 'P',
            BINARY 'F'
        )
    );

-- expected_grade는 nullable VARCHAR(20), 제약은 1개여야 합니다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'timetable_courses'
  AND column_name = 'expected_grade';

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
  AND table_constraint.constraint_name = 'ck_timetable_course_expected_grade';
