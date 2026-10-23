-- MySQL 8 이상. DB 백업 후 새 애플리케이션 배포 전에 실행합니다.
-- 기존 모집공고 내용은 보존합니다. 기간을 알 수 없는 기존 행은 날짜를 NULL로 유지합니다.
-- 각각의 컬럼을 확인하여 재실행 및 중간 실패 후 재시작을 지원합니다.

SET @club_period_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs'
       AND column_name = 'recruitment_start_date') = 0,
    'ALTER TABLE clubs ADD COLUMN recruitment_start_date DATE NULL',
    'SELECT 1'
);
PREPARE club_period_statement FROM @club_period_ddl;
EXECUTE club_period_statement;
DEALLOCATE PREPARE club_period_statement;

SET @club_period_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs'
       AND column_name = 'recruitment_end_date') = 0,
    'ALTER TABLE clubs ADD COLUMN recruitment_end_date DATE NULL',
    'SELECT 1'
);
PREPARE club_period_statement FROM @club_period_ddl;
EXECUTE club_period_statement;
DEALLOCATE PREPARE club_period_statement;

SELECT id, recruitment_start_date, recruitment_end_date FROM clubs ORDER BY id;
