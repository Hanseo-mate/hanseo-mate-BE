-- 동아리 모집공고 알림을 찜 사용자에게만 노출·발송하기 위한 운영 DB 증분 DDL입니다.
-- 운영은 ddl-auto=validate이므로 새 애플리케이션을 배포하기 전에 한 번 실행합니다.
-- 실행 전 DB 백업과 아래 사전 조회 결과 확인을 권장합니다.

SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('notifications', 'notification_outbox')
  AND column_name = 'target_user_id'
ORDER BY table_name;

-- 위 조회가 0행일 때 실행합니다.
ALTER TABLE notifications
    ADD COLUMN target_user_id BIGINT NULL,
    ADD INDEX idx_notifications_target_user_created_at (target_user_id, created_at);

ALTER TABLE notification_outbox
    ADD COLUMN target_user_id BIGINT NULL,
    ADD INDEX idx_notification_outbox_target_user (target_user_id);

-- 두 테이블 모두 target_user_id가 존재하고 nullable이면 정상입니다.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('notifications', 'notification_outbox')
  AND column_name = 'target_user_id'
ORDER BY table_name;
