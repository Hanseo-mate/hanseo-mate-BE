-- 축제 플로팅 버튼 설정 / append-only 감사 이력 (MySQL 8.0)
-- 운영에서는 백업 후 코드 배포 전에 적용한다. 기존 home_posters/app_popups는 변경하지 않는다.
-- 같은 이름의 테이블이 이미 있으면 아래 사전 조회 및 SHOW CREATE TABLE로 구조를 먼저 확인한다.
-- CREATE IF NOT EXISTS는 기존 테이블의 잘못된 구조를 고치지 않는다.

-- [1] 사전 조회: 첫 배포에는 0행이어야 한다.
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('app_feature_settings', 'app_feature_setting_audits')
ORDER BY table_name, ordinal_position;

-- [2] 신규 테이블 생성. 재실행 시 기존 데이터와 노출 상태를 보존한다.
CREATE TABLE IF NOT EXISTS app_feature_settings (
    setting_key VARCHAR(64) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'0',
    updated_at DATETIME(6) NULL,
    updated_by BIGINT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_feature_setting_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    setting_key VARCHAR(64) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    previous_enabled BIT(1) NOT NULL,
    new_enabled BIT(1) NOT NULL,
    request_ip VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_feature_setting_audits_key_id (setting_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 관리자 계정/설정 삭제로 감사 이력이 함께 삭제되지 않도록 cascading FK를 두지 않는다.
-- 최초 기본값은 false이며 아직 상태 변경이 없으므로 updated_at/updated_by는 NULL이다.
-- 기존 true를 false로 되돌리지 않는다.
INSERT INTO app_feature_settings (setting_key, enabled)
VALUES ('FESTIVAL_FLOATING_BUTTON', false)
ON DUPLICATE KEY UPDATE setting_key = 'FESTIVAL_FLOATING_BUTTON';

-- [3] 사후 확인. 기존 환경은 현재 값 유지, 최초 환경은 visible=0 / updated_at=NULL.
SELECT setting_key, enabled + 0 AS visible, updated_at, updated_by
FROM app_feature_settings
WHERE setting_key = 'FESTIVAL_FLOATING_BUTTON';

SELECT COUNT(*) AS audit_count
FROM app_feature_setting_audits
WHERE setting_key = 'FESTIVAL_FLOATING_BUTTON';
