-- link_url 기반 앱 팝업 테이블을 navigation 기반 계약으로 전환하는 증분 DDL입니다.
-- 기존 app_popups 테이블을 이미 만든 운영 DB에서만 실행합니다.
-- 아직 app_popups 테이블이 없다면 이 파일 대신 app-popup-migration-mysql.sql을 실행합니다.
-- MySQL DDL은 암시적으로 커밋되므로 실행 전 운영 DB 백업이 필수입니다.

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups'
ORDER BY ordinal_position;

-- 기존 HTTP URL과 사용자 정보가 포함된 URL은 신규 HTTPS 정책으로 자동 변환하지 않습니다.
-- 아래 결과가 0행인지 먼저 확인합니다. 결과가 있으면 HTTPS URL로 수정하거나 NULL로 정리한 뒤 실행합니다.
SELECT id, link_url
FROM app_popups
WHERE link_url IS NOT NULL
  AND TRIM(link_url) <> ''
  AND (
      LOWER(TRIM(link_url)) NOT LIKE 'https://%'
      OR TRIM(link_url) REGEXP '^https://[^/]*@'
  );

DROP PROCEDURE IF EXISTS migrate_app_popup_navigation;

DELIMITER $$

CREATE PROCEDURE migrate_app_popup_navigation()
BEGIN
    DECLARE invalid_url_count BIGINT DEFAULT 0;
    DECLARE schema_version_exists BIGINT DEFAULT 0;
    DECLARE navigation_type_exists BIGINT DEFAULT 0;
    DECLARE navigation_params_exists BIGINT DEFAULT 0;

    SELECT COUNT(*)
    INTO invalid_url_count
    FROM app_popups
    WHERE link_url IS NOT NULL
      AND TRIM(link_url) <> ''
      AND (
          LOWER(TRIM(link_url)) NOT LIKE 'https://%'
          OR TRIM(link_url) REGEXP '^https://[^/]*@'
      );

    IF invalid_url_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Invalid legacy popup URLs exist. Normalize them to HTTPS or NULL first.';
    END IF;

    SELECT COUNT(*)
    INTO schema_version_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_popups'
      AND column_name = 'navigation_schema_version';

    IF schema_version_exists = 0 THEN
        ALTER TABLE app_popups
            ADD COLUMN navigation_schema_version SMALLINT NULL AFTER image_url;
    END IF;

    SELECT COUNT(*)
    INTO navigation_type_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_popups'
      AND column_name = 'navigation_type';

    IF navigation_type_exists = 0 THEN
        ALTER TABLE app_popups
            ADD COLUMN navigation_type VARCHAR(40) NULL AFTER navigation_schema_version;
    END IF;

    SELECT COUNT(*)
    INTO navigation_params_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_popups'
      AND column_name = 'navigation_params';

    IF navigation_params_exists = 0 THEN
        ALTER TABLE app_popups
            ADD COLUMN navigation_params JSON NULL AFTER navigation_type;
    END IF;

    -- 기존 HTTPS link_url은 EXTERNAL_URL로 손실 없이 변환합니다.
    -- 실제 이동 정보가 생기므로 오늘 하루 숨김 키가 갱신되도록 revision도 1 증가시킵니다.
    UPDATE app_popups
    SET navigation_schema_version = 1,
        navigation_type = 'EXTERNAL_URL',
        navigation_params = JSON_OBJECT('url', TRIM(link_url)),
        revision = revision + 1
    WHERE link_url IS NOT NULL
      AND TRIM(link_url) <> ''
      AND navigation_type IS NULL;
END$$

DELIMITER ;

CALL migrate_app_popup_navigation();
DROP PROCEDURE migrate_app_popup_navigation;

-- link_url은 팝업 이미지 클릭용 선택 외부 URL로 계속 사용합니다.
-- 신규 애플리케이션은 HTTPS 절대 URL만 새로 저장합니다.

SELECT id,
       link_url,
       navigation_schema_version,
       navigation_type,
       navigation_params,
       revision
FROM app_popups
ORDER BY id;

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups'
  AND column_name IN (
      'navigation_schema_version',
      'navigation_type',
      'navigation_params'
  )
ORDER BY ordinal_position;
