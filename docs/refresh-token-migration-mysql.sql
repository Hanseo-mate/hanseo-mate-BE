-- 기존 운영 DB에 Refresh Token 저장 테이블을 추가하는 증분 DDL입니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 기존 DB에 실행하지 않습니다.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'refresh_tokens';

-- 위 조회 결과가 0행일 때만 아래 CREATE TABLE을 실행합니다.
CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    family_id VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    replaced_by_token_hash VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    INDEX idx_refresh_tokens_expires_at (expires_at),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 적용 결과를 확인합니다. 컬럼은 정확히 9개여야 합니다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'refresh_tokens'
ORDER BY ordinal_position;

SELECT index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'refresh_tokens'
GROUP BY index_name, non_unique
ORDER BY index_name;

SELECT constraint_name, referenced_table_name, delete_rule
FROM information_schema.referential_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'refresh_tokens';
