-- 사용자 선호 학생식당을 공통 선호 캠퍼스로 전환하는 운영 DB 증분 DDL입니다.
-- MySQL 8.0.16 이상에서 실행합니다.
-- 애플리케이션 쓰기를 중지한 점검 시간에 실행하고 실행 전 백업하세요.

-- 사전 확인
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user_accounts'
  AND column_name IN (
      'preferred_restaurant_type',
      'preferred_campus_code'
  )
ORDER BY column_name;

SELECT tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'user_accounts'
  AND tc.constraint_type = 'CHECK';

-- 기존 선호 학생식당 CHECK가 있으면 제거합니다.
SET @old_preference_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND constraint_name = 'ck_user_account_preferred_restaurant_type'
      AND constraint_type = 'CHECK'
);
SET @drop_old_preference_check_sql = IF(
    @old_preference_check_exists > 0,
    'ALTER TABLE user_accounts DROP CHECK ck_user_account_preferred_restaurant_type',
    'SELECT ''old preference CHECK does not exist'''
);
PREPARE drop_old_preference_check_statement
    FROM @drop_old_preference_check_sql;
EXECUTE drop_old_preference_check_statement;
DEALLOCATE PREPARE drop_old_preference_check_statement;

-- 기존 컬럼을 새 이름으로 바꿉니다. 이미 전환됐다면 건너뜁니다.
SET @old_preference_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_restaurant_type'
);
SET @new_preference_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_campus_code'
);
SET @rename_preference_column_sql = IF(
    @old_preference_column_exists = 1
        AND @new_preference_column_exists = 0,
    'ALTER TABLE user_accounts CHANGE COLUMN preferred_restaurant_type preferred_campus_code VARCHAR(20) NULL DEFAULT ''SEOSAN''',
    'SELECT ''preference column rename skipped'''
);
PREPARE rename_preference_column_statement
    FROM @rename_preference_column_sql;
EXECUTE rename_preference_column_statement;
DEALLOCATE PREPARE rename_preference_column_statement;

-- 두 컬럼이 모두 없는 비정상 상태라면 새 컬럼을 추가합니다.
SET @new_preference_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND column_name = 'preferred_campus_code'
);
SET @add_preference_column_sql = IF(
    @new_preference_column_exists = 0,
    'ALTER TABLE user_accounts ADD COLUMN preferred_campus_code VARCHAR(20) NULL DEFAULT ''SEOSAN'' AFTER role',
    'SELECT ''preferred_campus_code column already exists'''
);
PREPARE add_preference_column_statement
    FROM @add_preference_column_sql;
EXECUTE add_preference_column_statement;
DEALLOCATE PREPARE add_preference_column_statement;

-- 기존 값을 공통 캠퍼스 코드로 변환합니다.
UPDATE user_accounts
SET preferred_campus_code = CASE
    WHEN UPPER(TRIM(preferred_campus_code)) IN ('TAEAN', 'TAEAN_STUDENT')
        THEN 'TAEAN'
    ELSE 'SEOSAN'
END
WHERE preferred_campus_code IS NULL
   OR BINARY preferred_campus_code NOT IN (
       BINARY 'SEOSAN',
       BINARY 'TAEAN'
   );

ALTER TABLE user_accounts
    MODIFY COLUMN preferred_campus_code
        VARCHAR(20) NOT NULL DEFAULT 'SEOSAN';

-- 새 CHECK를 정확한 정의로 교체합니다.
SET @new_preference_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'user_accounts'
      AND constraint_name = 'ck_user_account_preferred_campus_code'
      AND constraint_type = 'CHECK'
);
SET @drop_new_preference_check_sql = IF(
    @new_preference_check_exists > 0,
    'ALTER TABLE user_accounts DROP CHECK ck_user_account_preferred_campus_code',
    'SELECT ''new preference CHECK does not exist'''
);
PREPARE drop_new_preference_check_statement
    FROM @drop_new_preference_check_sql;
EXECUTE drop_new_preference_check_statement;
DEALLOCATE PREPARE drop_new_preference_check_statement;

ALTER TABLE user_accounts
    ADD CONSTRAINT ck_user_account_preferred_campus_code CHECK (
        BINARY preferred_campus_code IN (
            BINARY 'SEOSAN',
            BINARY 'TAEAN'
        )
    );

-- 적용 결과 확인
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'user_accounts'
  AND column_name IN (
      'preferred_restaurant_type',
      'preferred_campus_code'
  )
ORDER BY column_name;

SELECT preferred_campus_code, COUNT(*) AS user_count
FROM user_accounts
GROUP BY preferred_campus_code
ORDER BY preferred_campus_code;

SELECT tc.constraint_name, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.table_name = 'user_accounts'
  AND tc.constraint_name = 'ck_user_account_preferred_campus_code';
