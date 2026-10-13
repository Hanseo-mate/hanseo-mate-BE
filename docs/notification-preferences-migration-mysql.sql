-- 운영 적용 전 대상 DB와 백업을 확인하세요. 앱 배포 전에 실행해야 합니다.
SELECT DATABASE() AS target_database;

-- push_devices 행 교체 및 로그인 연결 해제 후에도 같은 설치의 설정을 유지합니다.
-- 기존 기기는 설정 행이 없으면 전체 ON으로 처리하므로 일괄 INSERT하지 않습니다.
CREATE TABLE IF NOT EXISTS notification_preferences (
    installation_id VARCHAR(100) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    notice_milestone BIT(1) NOT NULL DEFAULT b'1',
    student_council_notice BIT(1) NOT NULL DEFAULT b'1',
    system_notice BIT(1) NOT NULL DEFAULT b'1',
    club_recruitment BIT(1) NOT NULL DEFAULT b'1',
    class_reminder BIT(1) NOT NULL DEFAULT b'1',
    shuttle_reservation BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (installation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 재실행해도 저장된 설정을 초기화하지 않습니다.
-- 기존에 다른 구조로 생성된 테이블은 IF NOT EXISTS로 수정되지 않으므로 결과를 확인하세요.
SHOW CREATE TABLE notification_preferences;
SELECT column_name, column_type, is_nullable, column_default, column_key
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'notification_preferences'
ORDER BY ordinal_position;
