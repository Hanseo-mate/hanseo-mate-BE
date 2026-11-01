-- MySQL 8 이상. 애플리케이션 쓰기를 중지하고 DB를 백업한 뒤 실행합니다.
-- 기존 동아리는 ID 오름차순을 보존하고, 재실행 시 저장된 순서는 유지합니다.
-- nullable 컬럼 추가 -> NULL 값만 채움 -> NOT NULL 전환 순서로 중단 후 재실행도 지원합니다.

SET @club_order_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs' AND column_name = 'display_order') = 0,
    'ALTER TABLE clubs ADD COLUMN display_order INT NULL DEFAULT NULL AFTER category',
    'SELECT 1'
);
PREPARE club_order_statement FROM @club_order_ddl;
EXECUTE club_order_statement;
DEALLOCATE PREPARE club_order_statement;

-- ROW_NUMBER를 포함한 파생 테이블은 구체화되어 동일 테이블 UPDATE에 사용할 수 있습니다.
UPDATE clubs AS target
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS initial_order
    FROM clubs
) AS initial ON initial.id = target.id
SET target.display_order = initial.initial_order
WHERE target.display_order IS NULL;

SET @club_order_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'clubs' AND column_name = 'display_order'
       AND (is_nullable = 'YES' OR COALESCE(column_default, '') <> '0')) > 0,
    'ALTER TABLE clubs MODIFY COLUMN display_order INT NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE club_order_statement FROM @club_order_ddl;
EXECUTE club_order_statement;
DEALLOCATE PREPARE club_order_statement;

SET @club_order_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'clubs'
       AND index_name = 'idx_clubs_display_order') = 0,
    'CREATE INDEX idx_clubs_display_order ON clubs (display_order, id)',
    'SELECT 1'
);
PREPARE club_order_statement FROM @club_order_ddl;
EXECUTE club_order_statement;
DEALLOCATE PREPARE club_order_statement;

SELECT id, name, display_order FROM clubs ORDER BY display_order, id;
