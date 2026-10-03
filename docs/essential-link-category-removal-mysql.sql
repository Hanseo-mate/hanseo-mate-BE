-- 기존 MySQL DB에 새 애플리케이션 배포 전에 실행합니다.
-- 카테고리 값은 삭제되므로 먼저 essential_links를 백업합니다.
-- 링크 ID, 이름, URL, 생성일, 수정일은 유지합니다.
-- 이미 제거한 DB에서는 다시 실행해도 변경하지 않습니다.
SET @essential_link_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'essential_links'
      AND column_name = 'category'
);
SET @essential_link_category_ddl = IF(
    @essential_link_category_exists > 0,
    'ALTER TABLE essential_links DROP COLUMN category',
    'SELECT 1'
);
PREPARE essential_link_category_statement FROM @essential_link_category_ddl;
EXECUTE essential_link_category_statement;
DEALLOCATE PREPARE essential_link_category_statement;

-- 적용 후 category 컬럼이 없는지 확인합니다.
SHOW COLUMNS FROM essential_links;
