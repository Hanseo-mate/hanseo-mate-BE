-- 기존 운영 DB의 사용자 계정에 선호 학생식당 설정을 추가하는 증분 DDL입니다.
-- MySQL 8.0.16 이상에서 실행합니다.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 운영 DB에 실행하지 않습니다.
-- 애플리케이션 쓰기를 중지한 점검 시간에 실행하고, 실행 전 백업과 아래 사전 조회 결과를 보관하세요.

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user_accounts'
  AND column_name = 'preferred_restaurant_type';

SELECT tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'user_accounts'
  AND tc.constraint_type = 'CHECK';

-- 컬럼이 없을 때만 추가합니다. 기존 회원은 MAIN_STUDENT로 채워집니다.
SET @preferred_restaurant_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_restaurant_type'
);
SET @preferred_restaurant_add_column_sql = IF(
    @preferred_restaurant_column_exists = 0,
    'ALTER TABLE user_accounts ADD COLUMN preferred_restaurant_type VARCHAR(20) NOT NULL DEFAULT ''MAIN_STUDENT'' AFTER role',
    'SELECT ''preferred_restaurant_type column already exists'''
);
PREPARE preferred_restaurant_add_column_statement
    FROM @preferred_restaurant_add_column_sql;
EXECUTE preferred_restaurant_add_column_statement;
DEALLOCATE PREPARE preferred_restaurant_add_column_statement;

-- 같은 이름의 CHECK는 정의·ENFORCED 상태와 관계없이 정확한 정의로 교체합니다.
SET @preferred_restaurant_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND constraint_name = 'ck_user_account_preferred_restaurant_type'
      AND constraint_type = 'CHECK'
);
SET @preferred_restaurant_drop_check_sql = IF(
    @preferred_restaurant_check_exists > 0,
    'ALTER TABLE user_accounts DROP CHECK ck_user_account_preferred_restaurant_type',
    'SELECT ''preferred restaurant CHECK does not exist'''
);
PREPARE preferred_restaurant_drop_check_statement
    FROM @preferred_restaurant_drop_check_sql;
EXECUTE preferred_restaurant_drop_check_statement;
DEALLOCATE PREPARE preferred_restaurant_drop_check_statement;

-- 잘못된 부분 적용으로 컬럼 폭이 좁아도 정규화 값을 저장할 수 있게 먼저 넓힙니다.
SET @preferred_restaurant_column_can_store_values = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_restaurant_type'
      AND character_maximum_length >= 20
);
SET @preferred_restaurant_widen_column_sql = IF(
    @preferred_restaurant_column_can_store_values = 1,
    'SELECT ''preferred_restaurant_type column width is sufficient''',
    'ALTER TABLE user_accounts MODIFY COLUMN preferred_restaurant_type VARCHAR(20) NULL DEFAULT ''MAIN_STUDENT'''
);
PREPARE preferred_restaurant_widen_column_statement
    FROM @preferred_restaurant_widen_column_sql;
EXECUTE preferred_restaurant_widen_column_statement;
DEALLOCATE PREPARE preferred_restaurant_widen_column_statement;

-- 대소문자와 후행 공백까지 정확히 일치하도록 기존 값을 정규화합니다.
UPDATE user_accounts
SET preferred_restaurant_type = CASE
    WHEN UPPER(TRIM(preferred_restaurant_type)) = 'TAEAN_STUDENT'
        THEN 'TAEAN_STUDENT'
    ELSE 'MAIN_STUDENT'
END
WHERE preferred_restaurant_type IS NULL
   OR BINARY preferred_restaurant_type NOT IN (
       BINARY 'MAIN_STUDENT',
       BINARY 'TAEAN_STUDENT'
   );

SET @preferred_restaurant_column_is_expected = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_restaurant_type'
      AND data_type = 'varchar'
      AND character_maximum_length = 20
      AND is_nullable = 'NO'
      AND BINARY column_default = BINARY 'MAIN_STUDENT'
);
SET @preferred_restaurant_modify_column_sql = IF(
    @preferred_restaurant_column_is_expected = 1,
    'SELECT ''preferred_restaurant_type column definition is already correct''',
    'ALTER TABLE user_accounts MODIFY COLUMN preferred_restaurant_type VARCHAR(20) NOT NULL DEFAULT ''MAIN_STUDENT'''
);
PREPARE preferred_restaurant_modify_column_statement
    FROM @preferred_restaurant_modify_column_sql;
EXECUTE preferred_restaurant_modify_column_statement;
DEALLOCATE PREPARE preferred_restaurant_modify_column_statement;

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_account_preferred_restaurant_type CHECK (
        BINARY preferred_restaurant_type IN (
            BINARY 'MAIN_STUDENT',
            BINARY 'TAEAN_STUDENT'
        )
    );

-- 적용 결과와 기존 회원 기본값을 확인합니다.
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user_accounts'
  AND column_name = 'preferred_restaurant_type';

SELECT tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'user_accounts'
  AND tc.constraint_name = 'ck_user_account_preferred_restaurant_type';

SELECT preferred_restaurant_type, COUNT(*) AS user_count
FROM user_accounts
GROUP BY preferred_restaurant_type
ORDER BY preferred_restaurant_type;
