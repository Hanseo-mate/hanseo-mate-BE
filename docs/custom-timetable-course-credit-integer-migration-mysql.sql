-- 직접 입력 시간표 과목의 학점에 0을 허용하고 사업적 최댓값을 제거합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- 기존 소수 학점 데이터를 보존하기 위해 scale=3은 유지하고, 신규 직접 입력은 API에서 정수만 허용합니다.
-- MySQL DDL은 암시적으로 commit되므로 적용 전 백업합니다.

DROP PROCEDURE IF EXISTS migrate_custom_timetable_course_credit;
DELIMITER //
CREATE PROCEDURE migrate_custom_timetable_course_credit()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database first';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name = 'custom_credit'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The timetable_courses.custom_credit column does not exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND constraint_name = 'ck_timetable_course_custom_credit'
          AND constraint_type = 'CHECK'
    ) THEN
        ALTER TABLE timetable_courses
            DROP CHECK ck_timetable_course_custom_credit;
    END IF;

    ALTER TABLE timetable_courses
        MODIFY COLUMN custom_credit DECIMAL(65,3) NULL;

    ALTER TABLE timetable_courses
        ADD CONSTRAINT ck_timetable_course_custom_credit CHECK (
            custom_credit IS NULL OR custom_credit >= 0
        );
END//
DELIMITER ;

CALL migrate_custom_timetable_course_credit();
DROP PROCEDURE migrate_custom_timetable_course_credit;

SHOW COLUMNS FROM timetable_courses LIKE 'custom_credit';

SELECT table_constraint.constraint_name,
       table_constraint.enforced,
       check_constraint.check_clause
FROM information_schema.table_constraints table_constraint
JOIN information_schema.check_constraints check_constraint
  ON check_constraint.constraint_schema = table_constraint.constraint_schema
 AND check_constraint.constraint_name = table_constraint.constraint_name
WHERE table_constraint.constraint_schema = DATABASE()
  AND table_constraint.table_name = 'timetable_courses'
  AND table_constraint.constraint_name = 'ck_timetable_course_custom_credit';
