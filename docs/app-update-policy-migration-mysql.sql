-- 앱 네이티브 빌드 업데이트 정책 / MySQL 8.0.16 이상
-- 코드 배포 전에 운영 DB 백업 및 SELECT DATABASE() 확인 후 실행한다.
-- DDL은 자동 커밋이다. 재실행은 데이터를 보존하지만 기존의 잘못된 테이블 구조를 복구하지 않는다.
SELECT DATABASE() AS target_database;
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('app_update_policies','app_update_platform_locks','app_update_commands','app_update_audits')
ORDER BY table_name, ordinal_position;

CREATE TABLE IF NOT EXISTS app_update_platform_locks (
    platform VARCHAR(16) NOT NULL,
    PRIMARY KEY (platform),
    CONSTRAINT chk_app_update_lock_platform CHECK (platform IN ('IOS','ANDROID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_update_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    platform VARCHAR(16) NOT NULL,
    latest_version VARCHAR(32) NOT NULL,
    latest_build BIGINT NOT NULL,
    force_update_enabled BIT(1) NOT NULL,
    minimum_supported_build BIGINT NULL,
    optional_update_enabled BIT(1) NOT NULL,
    store_url VARCHAR(500) NOT NULL,
    title VARCHAR(40) NOT NULL,
    message VARCHAR(300) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active_platform VARCHAR(16) NULL,
    scheduled_platform VARCHAR(16) NULL,
    effective_at DATETIME(6) NULL,
    revision BIGINT NOT NULL,
    store_availability_confirmed_at DATETIME(6) NULL,
    store_availability_confirmed_by BIGINT NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    published_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_update_active (active_platform),
    UNIQUE KEY uk_app_update_scheduled (scheduled_platform),
    INDEX idx_app_update_list (platform,status,id),
    INDEX idx_app_update_due (status,effective_at),
    CONSTRAINT chk_app_update_platform CHECK (platform IN ('IOS','ANDROID')),
    CONSTRAINT chk_app_update_status CHECK (status IN ('DRAFT','SCHEDULED','ACTIVE','SUPERSEDED','CANCELLED')),
    CONSTRAINT chk_app_update_latest CHECK (latest_build >= 1 AND revision >= 1),
    CONSTRAINT chk_app_update_minimum CHECK (
        (force_update_enabled = b'0' AND minimum_supported_build IS NULL)
        OR (force_update_enabled = b'1' AND minimum_supported_build IS NOT NULL
            AND minimum_supported_build >= 1 AND minimum_supported_build <= latest_build)
    ),
    CONSTRAINT chk_app_update_active_slot CHECK (
        (status = 'ACTIVE' AND active_platform IS NOT NULL AND active_platform = platform)
        OR (status <> 'ACTIVE' AND active_platform IS NULL)
    ),
    CONSTRAINT chk_app_update_scheduled_slot CHECK (
        (status = 'SCHEDULED' AND scheduled_platform IS NOT NULL AND scheduled_platform = platform)
        OR (status <> 'SCHEDULED' AND scheduled_platform IS NULL)
    ),
    CONSTRAINT chk_app_update_published CHECK (
        status NOT IN ('SCHEDULED','ACTIVE','SUPERSEDED')
        OR (effective_at IS NOT NULL AND published_at IS NOT NULL AND published_by IS NOT NULL
            AND store_availability_confirmed_at IS NOT NULL AND store_availability_confirmed_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_update_commands (
    -- 대소문자를 구분하는 멱등 키. 성공 결과는 정책과 같은 트랜잭션에 저장한다.
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    response_json LONGTEXT NULL,
    PRIMARY KEY (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS app_update_audits (
    id BIGINT NOT NULL AUTO_INCREMENT,
    policy_id BIGINT NULL,
    policy_revision BIGINT NULL,
    platform VARCHAR(16) NULL,
    event_type VARCHAR(32) NOT NULL,
    success BIT(1) NOT NULL,
    before_snapshot LONGTEXT NULL,
    after_snapshot LONGTEXT NULL,
    actor_id BIGINT NULL,
    published_by BIGINT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    request_ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    request_id VARCHAR(128) NOT NULL,
    failure_message VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    INDEX idx_app_update_audit_platform_time (platform,created_at,id),
    INDEX idx_app_update_audit_actor_time (actor_id,created_at),
    INDEX idx_app_update_audit_event_time (event_type,created_at),
    CONSTRAINT chk_app_update_audit_platform CHECK (platform IS NULL OR platform IN ('IOS','ANDROID')),
    CONSTRAINT chk_app_update_audit_event CHECK (
        event_type IN ('CREATE','UPDATE','PUBLISH','SCHEDULE','ACTIVATE','CANCEL','ROLLBACK','RELAX')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 계정/정책 삭제가 감사 이력에 전파되지 않도록 cascading FK를 두지 않는다.
-- ACTIVE/DRAFT 정책을 자동 생성하지 않는다. 최초 공개 API는 NONE, policy:null이다.
INSERT INTO app_update_platform_locks (platform) VALUES ('IOS'), ('ANDROID')
ON DUPLICATE KEY UPDATE platform = app_update_platform_locks.platform;

-- 사후 확인: 플랫폼별 ACTIVE/SCHEDULED 개수가 1을 넘는 결과가 없어야 한다.
SELECT platform, status, COUNT(*) AS policy_count
FROM app_update_policies
WHERE status IN ('ACTIVE','SCHEDULED')
GROUP BY platform, status
HAVING COUNT(*) > 1;

SELECT table_name, index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('app_update_policies','app_update_platform_locks','app_update_commands','app_update_audits')
ORDER BY table_name, index_name, seq_in_index;

SELECT COUNT(*) AS incomplete_command_count FROM app_update_commands WHERE response_json IS NULL;
SELECT COUNT(*) AS audit_count FROM app_update_audits;
