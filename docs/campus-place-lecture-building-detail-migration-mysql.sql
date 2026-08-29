-- 강의실 카테고리 장소의 상세 정보 테이블을 추가합니다.
-- 장소 카테고리와 상세 데이터는 자동 입력하지 않으며 사용자가 DB에서 직접 관리합니다.
-- 운영은 ddl-auto=validate이므로 애플리케이션 배포 전에 실행해야 합니다.

SET NAMES utf8mb4;

-- ============================================================================
-- [0] 항상 먼저 단독 실행
-- 세 테이블이 모두 없을 때만 [1]을 실행합니다. 1~2개이면 부분 생성 상태이므로
-- 중단하고 SHOW CREATE TABLE로 실제 상태를 확인합니다.
-- ============================================================================
SELECT DATABASE() AS selected_database,
       COUNT(*) AS lecture_detail_table_count,
       GROUP_CONCAT(table_name ORDER BY table_name) AS existing_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'campus_lecture_building_details',
      'campus_lecture_building_departments',
      'campus_lecture_building_facilities'
  );

-- ============================================================================
-- [1] lecture_detail_table_count=0일 때만 실행
-- ============================================================================
CREATE TABLE campus_lecture_building_details (
    place_id BIGINT NOT NULL,
    location_description VARCHAR(255) NULL,
    floor_count INT NULL,
    has_elevator BIT(1) NULL,
    operating_hours VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (place_id),
    CONSTRAINT fk_campus_lecture_building_detail_place
        FOREIGN KEY (place_id)
        REFERENCES campus_places (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_campus_lecture_building_floor_count
        CHECK (floor_count IS NULL OR floor_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campus_lecture_building_departments (
    place_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (place_id, sort_order),
    CONSTRAINT uk_campus_lecture_building_department
        UNIQUE (place_id, department_name),
    CONSTRAINT fk_campus_lecture_building_department_place
        FOREIGN KEY (place_id)
        REFERENCES campus_lecture_building_details (place_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE campus_lecture_building_facilities (
    place_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    facility_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (place_id, sort_order),
    CONSTRAINT uk_campus_lecture_building_facility
        UNIQUE (place_id, facility_name),
    CONSTRAINT fk_campus_lecture_building_facility_place
        FOREIGN KEY (place_id)
        REFERENCES campus_lecture_building_details (place_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- [2] 생성 후 확인
-- ============================================================================
SHOW CREATE TABLE campus_lecture_building_details;
SHOW CREATE TABLE campus_lecture_building_departments;
SHOW CREATE TABLE campus_lecture_building_facilities;

-- 수동 입력 형식 예시이며 실제 장소를 확인한 뒤 값을 입력합니다.
-- START TRANSACTION;
--
-- INSERT INTO campus_lecture_building_details (
--     place_id, location_description, floor_count, has_elevator,
--     operating_hours, created_at, updated_at
-- ) VALUES (
--     83, '서산캠퍼스', 5, b'1', '평일 09:00~22:00',
--     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
-- );
--
-- INSERT INTO campus_lecture_building_departments (
--     place_id, sort_order, department_name
-- ) VALUES
--     (83, 0, '컴퓨터공학과'),
--     (83, 1, '항공소프트웨어공학과');
--
-- INSERT INTO campus_lecture_building_facilities (
--     place_id, sort_order, facility_name
-- ) VALUES
--     (83, 0, '전산실습실'),
--     (83, 1, '학과사무실');
--
-- COMMIT;
