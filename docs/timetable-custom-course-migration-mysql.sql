-- 개인 시간표에 직접 입력한 과목을 저장할 수 있도록 시간표 과목 테이블을 확장합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 기존 DB에 실행하지 않습니다.
-- MySQL DDL은 암시적으로 commit되므로 애플리케이션 쓰기를 중지하고 백업한 뒤 적용합니다.
-- CHECK 제약을 실제로 강제하는 MySQL 8.0.16 이상에서 한 번만 실행합니다.

DROP PROCEDURE IF EXISTS assert_custom_timetable_course_migration_ready;
DELIMITER //
CREATE PROCEDURE assert_custom_timetable_course_migration_ready()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database before running the custom course migration';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The timetable_courses table does not exist';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name IN (
              'course_offering_id',
              'custom_course_name',
              'custom_credit'
          )
    ) <> 3 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Apply the timetable and grade override migrations first';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND column_name IN (
              'custom_day_of_week',
              'custom_start_time',
              'custom_end_time'
          )
    ) OR EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'timetable_courses'
          AND constraint_name IN (
              'ck_timetable_course_custom_day',
              'ck_timetable_course_custom_time',
              'ck_timetable_course_source'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Custom course migration is already or partially applied';
    END IF;
END//
DELIMITER ;

CALL assert_custom_timetable_course_migration_ready();
DROP PROCEDURE assert_custom_timetable_course_migration_ready;

ALTER TABLE timetable_courses
    MODIFY COLUMN course_offering_id BINARY(16) NULL,
    ADD COLUMN custom_day_of_week VARCHAR(20) NULL
        AFTER custom_credit,
    ADD COLUMN custom_start_time TIME NULL
        AFTER custom_day_of_week,
    ADD COLUMN custom_end_time TIME NULL
        AFTER custom_start_time,
    ADD CONSTRAINT ck_timetable_course_custom_day CHECK (
        custom_day_of_week IS NULL
        OR BINARY custom_day_of_week IN (
            BINARY 'MONDAY',
            BINARY 'TUESDAY',
            BINARY 'WEDNESDAY',
            BINARY 'THURSDAY',
            BINARY 'FRIDAY',
            BINARY 'SATURDAY',
            BINARY 'SUNDAY'
        )
    ),
    ADD CONSTRAINT ck_timetable_course_custom_time CHECK (
        (custom_start_time IS NULL AND custom_end_time IS NULL)
        OR (
            custom_start_time IS NOT NULL
            AND custom_end_time IS NOT NULL
            AND custom_start_time < custom_end_time
        )
    ),
    ADD CONSTRAINT ck_timetable_course_source CHECK (
        (
            course_offering_id IS NOT NULL
            AND custom_day_of_week IS NULL
            AND custom_start_time IS NULL
            AND custom_end_time IS NULL
        )
        OR (
            course_offering_id IS NULL
            AND custom_course_name IS NOT NULL
            AND custom_credit IS NOT NULL
            AND custom_day_of_week IS NOT NULL
            AND custom_start_time IS NOT NULL
            AND custom_end_time IS NOT NULL
        )
    );

-- course_offering_id는 nullable, 직접 입력용 세 컬럼은 nullable이어야 합니다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'timetable_courses'
  AND column_name IN (
      'course_offering_id',
      'custom_day_of_week',
      'custom_start_time',
      'custom_end_time'
  )
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
      'ck_timetable_course_custom_day',
      'ck_timetable_course_custom_time',
      'ck_timetable_course_source'
  )
ORDER BY table_constraint.constraint_name;

-- 결과는 0이어야 합니다.
SELECT COUNT(*) AS invalid_custom_course_rows
FROM timetable_courses
WHERE NOT (
    (
        course_offering_id IS NOT NULL
        AND custom_day_of_week IS NULL
        AND custom_start_time IS NULL
        AND custom_end_time IS NULL
    )
    OR (
        course_offering_id IS NULL
        AND custom_course_name IS NOT NULL
        AND custom_credit IS NOT NULL
        AND custom_day_of_week IS NOT NULL
        AND custom_start_time IS NOT NULL
        AND custom_end_time IS NOT NULL
        AND custom_start_time < custom_end_time
    )
);
