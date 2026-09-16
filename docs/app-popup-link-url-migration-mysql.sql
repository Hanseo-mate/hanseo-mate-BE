-- navigation 기반 app_popups 테이블에 이미지 클릭용 link_url을 추가합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 코드보다 먼저 실행합니다.
-- 과거 link_url 기반 테이블처럼 컬럼이 이미 있으면 아무것도 변경하지 않습니다.

DROP PROCEDURE IF EXISTS migrate_app_popup_link_url;

DELIMITER $$

CREATE PROCEDURE migrate_app_popup_link_url()
BEGIN
    DECLARE popup_table_exists BIGINT DEFAULT 0;
    DECLARE link_url_exists BIGINT DEFAULT 0;

    SELECT COUNT(*)
    INTO popup_table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'app_popups';

    IF popup_table_exists = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'app_popups table is missing. Apply app-popup-migration-mysql.sql first.';
    END IF;

    SELECT COUNT(*)
    INTO link_url_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'app_popups'
      AND column_name = 'link_url';

    IF link_url_exists = 0 THEN
        ALTER TABLE app_popups
            ADD COLUMN link_url VARCHAR(2048) NULL AFTER image_url;
    END IF;
END$$

DELIMITER ;

CALL migrate_app_popup_link_url();
DROP PROCEDURE migrate_app_popup_link_url;

SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'app_popups'
  AND column_name = 'link_url';
