-- MySQL 8 이상. 새 애플리케이션 배포 전에 실행합니다.
-- 기존 동아리는 false로 유지하며 모집공고 내용, 기간, 링크는 변경하지 않습니다.

SET @club_always_open_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs'
       AND column_name = 'recruitment_always_open') = 0,
    'ALTER TABLE clubs ADD COLUMN recruitment_always_open BIT(1) NOT NULL DEFAULT b''0''',
    'SELECT 1'
);
PREPARE club_always_open_statement FROM @club_always_open_ddl;
EXECUTE club_always_open_statement;
DEALLOCATE PREPARE club_always_open_statement;

SHOW COLUMNS FROM clubs LIKE 'recruitment_always_open';
