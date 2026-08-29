-- 기존 운영 DB에 캠퍼스 건물 좌표와 별칭 테이블을 추가하는 증분 SQL입니다.
-- 운영은 ddl-auto=validate이므로 반드시 애플리케이션 코드보다 먼저 실행합니다.
-- MySQL DDL은 암시적으로 커밋되므로 실패 후 바로 재실행하지 말고 실제 상태를 확인합니다.
--
-- 실행 경로는 대상 테이블 수에 따라 다릅니다. 먼저 [0-A]만 실행하고 결과를 확인합니다.
--   0개: 이 파일 전체를 처음부터 실행합니다([1] DDL -> [0-B] 확인 -> [2] seed -> [3] 검증).
--   1개: 부분 생성 상태입니다. 즉시 중단하고 SHOW CREATE TABLE 및 생성 경위를 확인합니다.
--        누락 테이블을 임의로 만들거나 seed를 실행하지 않습니다.
--   2개: [1]을 실행하지 않습니다. 두 테이블이 올바른 구조이고 모두 0행인지 [0-B]로
--        확인한 뒤, 로컬 ddl-auto=update가 만든 빈 테이블에 한해서 [2] seed 구간만
--        선택 실행하고 마지막으로 [3]을 실행합니다.
-- 이미 데이터가 한 건이라도 있으면 seed를 다시 실행하지 말고 중단합니다.

SET NAMES utf8mb4;

-- ============================================================================
-- [0-A] 항상 먼저 단독 실행: 테이블 개수 preflight
-- ============================================================================
SELECT DATABASE() AS selected_database,
       COUNT(*) AS campus_location_table_count,
       GROUP_CONCAT(table_name ORDER BY table_name) AS existing_tables,
       CASE COUNT(*)
           WHEN 0 THEN 'FULL_RUN_FROM_SECTION_1'
           WHEN 1 THEN 'STOP_AND_INSPECT_PARTIAL_SCHEMA'
           WHEN 2 THEN 'SKIP_SECTION_1_AND_RUN_SECTION_0_B'
           ELSE 'STOP_AND_INSPECT_UNEXPECTED_STATE'
       END AS next_action
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('campus_buildings', 'campus_building_aliases');

-- selected_database가 의도한 DB인지 먼저 확인합니다.
-- campus_location_table_count=1이면 여기서 반드시 중단합니다.

-- ============================================================================
-- [1] 테이블이 0개일 때만 실행: DDL
-- 두 테이블이 이미 있으면 이 구간을 실행하지 않습니다.
-- ============================================================================
CREATE TABLE campus_buildings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campus_code VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    canonical_name_key VARCHAR(255) NOT NULL,
    latitude DECIMAL(12, 9) NOT NULL,
    longitude DECIMAL(12, 9) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_building_campus_name_key
        UNIQUE (campus_code, canonical_name_key),
    CONSTRAINT uk_campus_building_id_campus
        UNIQUE (id, campus_code),
    INDEX ix_campus_building_campus (campus_code),
    CONSTRAINT ck_campus_building_campus_code
        CHECK (campus_code IN ('SEOSAN', 'TAEAN')),
    CONSTRAINT ck_campus_building_latitude
        CHECK (latitude BETWEEN -90.000000000 AND 90.000000000),
    CONSTRAINT ck_campus_building_longitude
        CHECK (longitude BETWEEN -180.000000000 AND 180.000000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campus_building_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    building_id BIGINT NOT NULL,
    campus_code VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    alias_name VARCHAR(255) NOT NULL,
    alias_key VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_building_alias_key_campus
        UNIQUE (alias_key, campus_code),
    INDEX ix_campus_building_alias_building_campus
        (building_id, campus_code),
    CONSTRAINT fk_campus_building_alias_building
        FOREIGN KEY (building_id, campus_code)
        REFERENCES campus_buildings (id, campus_code)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- [0-B] [1] 직후 또는 기존 테이블이 정확히 2개일 때만 실행: 구조와 빈 상태 확인
-- ============================================================================
-- 운영 DB라면 두 SHOW CREATE TABLE 결과가 위 [1] DDL과 제약까지 일치해야 합니다.
-- 로컬 ddl-auto=update 테이블은 적어도 컬럼/타입/nullability, PK, 두 UNIQUE 제약,
-- alias의 (building_id, campus_code) 복합 FK가 엔티티 매핑과 일치해야 합니다.
-- Hibernate가 만든 로컬 제약 이름이나 부가 CHECK/ON DELETE 설정은 다를 수 있으므로,
-- 이 seed-only 경로로 채운 로컬 스키마를 운영 스키마로 간주하면 안 됩니다.
-- 필수 구조가 하나라도 다르면 seed하지 말고 중단합니다.
SHOW CREATE TABLE campus_buildings;
SHOW CREATE TABLE campus_building_aliases;

-- seed 실행 전 두 row_count가 모두 0이어야 합니다.
SELECT 'campus_buildings' AS table_name, COUNT(*) AS row_count
FROM campus_buildings
UNION ALL
SELECT 'campus_building_aliases' AS table_name, COUNT(*) AS row_count
FROM campus_building_aliases;

-- ============================================================================
-- [2] SEED-ONLY SECTION
-- 실행 허용: [1]로 방금 만든 빈 테이블, 또는 [0-B]를 통과한 로컬 ddl-auto=update 빈 테이블.
-- 기존 테이블이 2개인 경우 이 표시부터 [2] 끝 표시까지만 선택 실행합니다.
-- 두 테이블 중 하나라도 데이터가 있으면 이 구간을 실행하지 않습니다.
-- --force 옵션 없이 실행합니다. INSERT 하나라도 실패하면 COMMIT을 실행하지 말고
-- ROLLBACK 후 실제 행 수를 다시 확인합니다.
-- ============================================================================
START TRANSACTION;

SET @campus_location_seeded_at = CURRENT_TIMESTAMP(6);

INSERT INTO campus_buildings (
    id, campus_code, canonical_name, canonical_name_key,
    latitude, longitude, created_at, updated_at
) VALUES
    (1, 'SEOSAN', '공학관', '공학관',
     36.690884000, 126.585761000, @campus_location_seeded_at, @campus_location_seeded_at),
    (2, 'SEOSAN', '인문사회관', '인문사회관',
     36.690100000, 126.585907000, @campus_location_seeded_at, @campus_location_seeded_at),
    (3, 'SEOSAN', '자악관', '자악관',
     36.691490000, 126.588935000, @campus_location_seeded_at, @campus_location_seeded_at),
    (4, 'SEOSAN', '보건의료학관', '보건의료학관',
     36.690237000, 126.581944000, @campus_location_seeded_at, @campus_location_seeded_at),
    (5, 'SEOSAN', '건축토목공학관', '건축토목공학관',
     36.691361000, 126.583607000, @campus_location_seeded_at, @campus_location_seeded_at),
    (6, 'SEOSAN', '인곡관', '인곡관',
     36.691789000, 126.584722000, @campus_location_seeded_at, @campus_location_seeded_at),
    (7, 'SEOSAN', '예술관', '예술관',
     36.689406000, 126.587976000, @campus_location_seeded_at, @campus_location_seeded_at),
    (8, 'SEOSAN', '이학관', '이학관',
     36.690669000, 126.581760000, @campus_location_seeded_at, @campus_location_seeded_at),
    (9, 'SEOSAN', '영암관', '영암관',
     36.691341000, 126.582453000, @campus_location_seeded_at, @campus_location_seeded_at),
    (10, 'SEOSAN', '심운관', '심운관',
     36.691160000, 126.586480000, @campus_location_seeded_at, @campus_location_seeded_at),
    (11, 'SEOSAN', '영암체육관', '영암체육관',
     36.691580000, 126.588189000, @campus_location_seeded_at, @campus_location_seeded_at),
    (12, 'TAEAN', '태안 강의동(본관)', '태안강의동(본관)',
     36.594581000, 126.294056000, @campus_location_seeded_at, @campus_location_seeded_at),
    (13, 'TAEAN', '태안 실습2동', '태안실습2동',
     36.593520000, 126.294879000, @campus_location_seeded_at, @campus_location_seeded_at),
    (14, 'TAEAN', '항공기술교육센터(메디치)', '항공기술교육센터(메디치)',
     36.596492000, 126.292215000, @campus_location_seeded_at, @campus_location_seeded_at);

INSERT INTO campus_building_aliases (
    id, building_id, campus_code, alias_name, alias_key,
    created_at, updated_at
) VALUES
    (1, 1, 'SEOSAN', '공학관', '공학관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (2, 2, 'SEOSAN', '인문사회관', '인문사회관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (3, 2, 'SEOSAN', '인문관', '인문관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (4, 3, 'SEOSAN', '자악관', '자악관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (5, 3, 'SEOSAN', '본관', '본관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (6, 3, 'SEOSAN', '서산 본관', '서산본관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (7, 4, 'SEOSAN', '보건의료학관', '보건의료학관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (8, 4, 'SEOSAN', '보건관', '보건관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (9, 5, 'SEOSAN', '건축토목공학관', '건축토목공학관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (10, 5, 'SEOSAN', '건축관', '건축관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (11, 6, 'SEOSAN', '인곡관', '인곡관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (12, 7, 'SEOSAN', '예술관', '예술관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (13, 8, 'SEOSAN', '이학관', '이학관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (14, 9, 'SEOSAN', '영암관', '영암관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (15, 10, 'SEOSAN', '심운관', '심운관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (16, 11, 'SEOSAN', '영암체육관', '영암체육관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (17, 11, 'SEOSAN', '영암체육관(서산)', '영암체육관(서산)',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (18, 11, 'SEOSAN', '서산 영암체육관', '서산영암체육관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (19, 12, 'TAEAN', '본관', '본관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (20, 12, 'TAEAN', '태안 강의동(본관)', '태안강의동(본관)',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (21, 12, 'TAEAN', '태안 강의동 본관', '태안강의동본관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (22, 12, 'TAEAN', '비행교육원', '비행교육원',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (23, 13, 'TAEAN', '실습2동', '실습2동',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (24, 13, 'TAEAN', '태안 실습2동', '태안실습2동',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (25, 14, 'TAEAN', '항공기술교육센터(메디치)', '항공기술교육센터(메디치)',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (26, 14, 'TAEAN', '태안 항공기술교육센터(메디치)', '태안항공기술교육센터(메디치)',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (27, 14, 'TAEAN', '태안 항공기술센터(메디치)', '태안항공기술센터(메디치)',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (28, 12, 'TAEAN', '태안본관', '태안본관',
     @campus_location_seeded_at, @campus_location_seeded_at),
    (29, 14, 'TAEAN', '항공기술교육원', '항공기술교육원',
     @campus_location_seeded_at, @campus_location_seeded_at);

COMMIT;

-- [2] SEED-ONLY SECTION END

-- ============================================================================
-- [3] seed 후 검증
-- ============================================================================
-- 건물은 SEOSAN 11건, TAEAN 3건이어야 합니다.
SELECT campus_code, COUNT(*) AS building_count
FROM campus_buildings
GROUP BY campus_code
ORDER BY campus_code;

-- 별칭은 SEOSAN 18건, TAEAN 11건이어야 합니다.
SELECT campus_code, COUNT(*) AS alias_count
FROM campus_building_aliases
GROUP BY campus_code
ORDER BY campus_code;

-- '본관'은 서산 자악관과 태안 강의동 각각 1건이어야 합니다.
SELECT a.campus_code, a.alias_name, b.canonical_name,
       b.latitude, b.longitude
FROM campus_building_aliases a
JOIN campus_buildings b
  ON b.id = a.building_id
 AND b.campus_code = a.campus_code
WHERE a.alias_key = '본관'
ORDER BY a.campus_code;

-- 아래 세 검증은 모두 0이어야 합니다.
SELECT COUNT(*) AS duplicate_building_count
FROM (
    SELECT campus_code, canonical_name_key
    FROM campus_buildings
    GROUP BY campus_code, canonical_name_key
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS duplicate_alias_count
FROM (
    SELECT campus_code, alias_key
    FROM campus_building_aliases
    GROUP BY campus_code, alias_key
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS orphan_or_cross_campus_alias_count
FROM campus_building_aliases a
LEFT JOIN campus_buildings b
  ON b.id = a.building_id
 AND b.campus_code = a.campus_code
WHERE b.id IS NULL;

SELECT COUNT(*) AS invalid_coordinate_count
FROM campus_buildings
WHERE latitude NOT BETWEEN -90.000000000 AND 90.000000000
   OR longitude NOT BETWEEN -180.000000000 AND 180.000000000;
