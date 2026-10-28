-- MySQL 8 이상. DB 백업 후 새 애플리케이션 배포 전에 실행합니다.
-- 기존 모집공고 내용과 기간은 보존하며, 기존 행의 링크는 NULL로 둡니다.

SET @club_url_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs'
       AND column_name = 'recruitment_url') = 0,
    'ALTER TABLE clubs ADD COLUMN recruitment_url VARCHAR(2048) NULL',
    'SELECT 1'
);
PREPARE club_url_statement FROM @club_url_ddl;
EXECUTE club_url_statement;
DEALLOCATE PREPARE club_url_statement;

SHOW COLUMNS FROM clubs LIKE 'recruitment_url';
