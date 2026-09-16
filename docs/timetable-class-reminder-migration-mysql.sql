-- 시간표 수업 시작 1시간 전 알림: 운영 코드 배포 전에 적용합니다.
-- 기존 DB에는 이 증분 파일만 실행합니다. database-schema-mysql.sql은 빈 DB 전용입니다.
-- 사전: 대상 DB 선택, 백업. MySQL DDL은 암시적으로 commit됩니다.
-- 아래 프로시저는 없는 항목만 추가하므로 재실행할 수 있습니다.
-- 이미 같은 이름의 항목이 있으면 변경하지 않으므로 마지막 검증 결과를 확인합니다.

SELECT DATABASE() AS target_database;
SELECT table_name, column_name, column_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'notification_outbox' AND column_name IN ('target_user_id', 'expires_at', 'status'))
       OR table_name = 'timetable_class_reminders');

DROP PROCEDURE IF EXISTS migrate_timetable_class_reminders;
DELIMITER //
CREATE PROCEDURE migrate_timetable_class_reminders()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target database first';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notification_outbox'
          AND column_name = 'target_user_id'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'timetable_courses'
          AND column_name = 'custom_start_time'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Apply targeted notifications and custom timetable course migrations first';
    END IF;

    -- Hibernate가 과거에 native ENUM으로 만든 DB도 EXPIRED를 저장할 수 있도록 합니다.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notification_outbox'
          AND column_name = 'status' AND data_type = 'enum'
    ) THEN
        ALTER TABLE notification_outbox MODIFY COLUMN status VARCHAR(20) NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'notification_outbox'
          AND column_name = 'expires_at'
    ) THEN
        ALTER TABLE notification_outbox ADD COLUMN expires_at DATETIME(6) NULL;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'notification_outbox'
          AND index_name = 'idx_notification_outbox_status'
    ) THEN
        ALTER TABLE notification_outbox ADD INDEX idx_notification_outbox_status (status, id);
    END IF;

    CREATE TABLE IF NOT EXISTS timetable_class_reminders (
        id BIGINT NOT NULL AUTO_INCREMENT,
        timetable_course_id BIGINT NOT NULL,
        starts_at DATETIME(6) NOT NULL COMMENT 'Class start date/time in Asia/Seoul',
        PRIMARY KEY (id),
        CONSTRAINT uk_timetable_class_reminder_occurrence UNIQUE (timetable_course_id, starts_at),
        CONSTRAINT fk_class_reminder_timetable_course FOREIGN KEY (timetable_course_id)
            REFERENCES timetable_courses (id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
END//
DELIMITER ;

CALL migrate_timetable_class_reminders();
DROP PROCEDURE migrate_timetable_class_reminders;

-- expires_at: nullable datetime(6). 기존 일반 알림은 NULL을 그대로 유지합니다.
SHOW COLUMNS FROM notification_outbox LIKE 'expires_at';
SHOW COLUMNS FROM notification_outbox LIKE 'status';
SHOW INDEX FROM notification_outbox WHERE Key_name = 'idx_notification_outbox_status';
SHOW CREATE TABLE timetable_class_reminders;

-- 배포 후 확인: schedule + class_start_reminder 알림은 target_user_id가 있어야 합니다.
SELECT id, target_user_id, status, expires_at,
       JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.startsAt')) AS class_starts_at
FROM notification_outbox
WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.data.subType')) = 'class_start_reminder'
ORDER BY id DESC
LIMIT 20;
