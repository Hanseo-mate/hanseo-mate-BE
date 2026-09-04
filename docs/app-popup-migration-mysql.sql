-- 기존 운영 DB에 앱 시작 팝업 테이블을 추가하는 증분 DDL입니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 기존 DB에 실행하지 않습니다.

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups';

-- 위 조회 결과가 0행일 때만 아래 CREATE TABLE을 실행합니다.
CREATE TABLE app_popups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    image_url VARCHAR(2048) NULL,
    link_url VARCHAR(2048) NULL,
    navigation_schema_version SMALLINT NULL,
    navigation_type VARCHAR(40) NULL,
    navigation_params JSON NULL,
    enabled BIT(1) NOT NULL,
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    display_order INT NOT NULL,
    revision BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_app_popups_exposure (enabled, starts_at, ends_at, display_order, id),
    INDEX idx_app_popups_created_at (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 적용 결과를 확인합니다. 컬럼은 정확히 15개여야 합니다.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups'
ORDER BY ordinal_position;

SELECT index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups'
GROUP BY index_name, non_unique
ORDER BY index_name;
