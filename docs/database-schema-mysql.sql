-- HanseoMate 전체 MySQL 스키마
-- 대상: 완전히 비어 있는 새 데이터베이스
-- 기준: MySQL 8.0 / InnoDB / utf8mb4
--
-- 이 파일은 기존 테이블을 변경하거나 삭제하지 않는다.
-- 테이블이 남아 있는 데이터베이스에서 실행하면 CREATE TABLE 단계에서 실패하므로,
-- 반드시 새로 만든 빈 hanseo_mate 데이터베이스에 적용한다.

SET NAMES utf8mb4;

CREATE TABLE essential_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    category VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE home_posters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    image_url VARCHAR(2048) NOT NULL,
    link_url VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_council_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(100) NOT NULL,
    content LONGTEXT NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_student_council_notices_created_at (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE system_notices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(500) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_system_notices_created_at (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_council_notice_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_student_council_notice_images_notice (notice_id, id),
    CONSTRAINT fk_student_council_notice_images_notice
        FOREIGN KEY (notice_id)
        REFERENCES student_council_notices (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_council_notice_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_council_notice_attachment_storage_key UNIQUE (storage_key),
    INDEX idx_student_council_notice_attachments_notice (notice_id, id),
    CONSTRAINT fk_student_council_notice_attachments_notice
        FOREIGN KEY (notice_id)
        REFERENCES student_council_notices (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_council_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_calendar_event_dates (start_date, end_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE school_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_school_calendar_events_dates (start_date, end_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    preferred_restaurant_type VARCHAR(20) NOT NULL DEFAULT 'MAIN_STUDENT',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_login_id UNIQUE (login_id),
    CONSTRAINT ck_user_account_preferred_restaurant_type CHECK (
        BINARY preferred_restaurant_type IN (
            BINARY 'MAIN_STUDENT',
            BINARY 'TAEAN_STUDENT'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    installation_id VARCHAR(100) NOT NULL,
    expo_push_token VARCHAR(200) NOT NULL,
    platform VARCHAR(10) NOT NULL,
    project_id VARCHAR(100) NOT NULL,
    app_version VARCHAR(20) NOT NULL,
    is_active BIT(1) NOT NULL,
    last_registered_at DATETIME(6) NOT NULL,
    disabled_at DATETIME(6) NULL,
    last_error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_devices_installation UNIQUE (installation_id),
    CONSTRAINT uk_push_devices_expo_token UNIQUE (expo_push_token),
    INDEX idx_push_devices_user (user_id),
    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE push_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expo_ticket_id VARCHAR(100) NOT NULL,
    outbox_id BIGINT NOT NULL,
    push_device_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(100) NULL,
    checked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_push_tickets_push_device (push_device_id),
    CONSTRAINT fk_push_tickets_push_device
        FOREIGN KEY (push_device_id) REFERENCES push_devices (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE personal_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_personal_calendar_events_owner_dates (
        owner_id,
        start_date,
        end_date,
        id
    ),
    CONSTRAINT fk_personal_calendar_events_owner
        FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE semesters (
    id BINARY(16) NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_year_term UNIQUE (academic_year, semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE timetables (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_timetable_owner_term
        UNIQUE (owner_id, academic_year, semester),
    CONSTRAINT fk_timetables_owner
        FOREIGN KEY (owner_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academic_units (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    major_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_unit_master_key UNIQUE (master_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 공백이 아닌 course_code가 과목 identity다. MySQL UNIQUE는 NULL을 여러 건 허용하므로
-- 코드 없는 수입 행은 서로 다른 master_key와 id로 각각 저장할 수 있다.
CREATE TABLE courses (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    course_code VARCHAR(100) NULL,
    course_name VARCHAR(255) NULL,
    academic_unit_id BINARY(16) NULL,
    curriculum_type VARCHAR(30) NULL,
    section_no VARCHAR(100) NULL,
    credit DECIMAL(8,3) NULL,
    class_hours DECIMAL(8,3) NULL,
    instructor_name VARCHAR(255) NULL,
    target_grade INT NULL,
    common_grade BIT(1) NULL,
    team_teaching BIT(1) NULL,
    note VARCHAR(2000) NULL,
    eligibility_note VARCHAR(2000) NULL,
    schedule_text VARCHAR(2000) NULL,
    classroom_text VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_master_key UNIQUE (master_key),
    CONSTRAINT uk_course_code UNIQUE (course_code),
    INDEX ix_course_name (course_name),
    INDEX ix_course_instructor (instructor_name),
    INDEX ix_course_curriculum (curriculum_type),
    CONSTRAINT fk_course_academic_unit
        FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE classrooms (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    campus_code VARCHAR(100) NULL,
    building_name VARCHAR(255) NULL,
    room_number VARCHAR(100) NULL,
    original_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_classroom_master_key UNIQUE (master_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE campus_places (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campus_code VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    place_name VARCHAR(255) NOT NULL,
    place_name_key VARCHAR(255) NOT NULL,
    category VARCHAR(40) COLLATE utf8mb4_bin NULL,
    one_line_description VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    image_url VARCHAR(2048) NULL,
    latitude DECIMAL(12, 9) NOT NULL,
    longitude DECIMAL(12, 9) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_place_campus_name_key
        UNIQUE (campus_code, place_name_key),
    INDEX ix_campus_place_campus (campus_code),
    CONSTRAINT ck_campus_place_campus_code
        CHECK (campus_code IN ('SEOSAN', 'TAEAN')),
    CONSTRAINT ck_campus_place_category
        CHECK (
            category IS NULL OR category IN (
                'RESTAURANT',
                'CAFE',
                'LECTURE_BUILDING',
                'CONVENIENCE_FACILITY'
            )
        ),
    CONSTRAINT ck_campus_place_latitude
        CHECK (latitude BETWEEN -90.000000000 AND 90.000000000),
    CONSTRAINT ck_campus_place_longitude
        CHECK (longitude BETWEEN -180.000000000 AND 180.000000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

START TRANSACTION;

SET @campus_place_seeded_at = CURRENT_TIMESTAMP(6);

INSERT INTO campus_places (
    id, campus_code, place_name, place_name_key,
    latitude, longitude, created_at, updated_at
) VALUES
    (1, 'SEOSAN', '신선대 식당', '신선대식당',
     36.692232000, 126.574652000, @campus_place_seeded_at, @campus_place_seeded_at),
    (2, 'SEOSAN', '가배앤빈', '가배앤빈',
     36.691166000, 126.574659000, @campus_place_seeded_at, @campus_place_seeded_at),
    (3, 'SEOSAN', '가시버시', '가시버시',
     36.690613000, 126.575891000, @campus_place_seeded_at, @campus_place_seeded_at),
    (4, 'SEOSAN', '한서마당', '한서마당',
     36.690527000, 126.576010000, @campus_place_seeded_at, @campus_place_seeded_at),
    (5, 'SEOSAN', '파비 기사식당', '파비기사식당',
     36.689521000, 126.576797000, @campus_place_seeded_at, @campus_place_seeded_at),
    (6, 'SEOSAN', '시골밥상', '시골밥상',
     36.689385000, 126.576913000, @campus_place_seeded_at, @campus_place_seeded_at),
    (7, 'SEOSAN', '가야산우리지리', '가야산우리지리',
     36.689303000, 126.576962000, @campus_place_seeded_at, @campus_place_seeded_at),
    (8, 'SEOSAN', '홍은이닭지그리', '홍은이닭지그리',
     36.689338000, 126.576970000, @campus_place_seeded_at, @campus_place_seeded_at),
    (9, 'SEOSAN', '다담', '다담',
     36.689245000, 126.577037000, @campus_place_seeded_at, @campus_place_seeded_at),
    (10, 'SEOSAN', '자라용봉탕', '자라용봉탕',
     36.689027000, 126.577268000, @campus_place_seeded_at, @campus_place_seeded_at),
    (11, 'SEOSAN', '블루플레이스', '블루플레이스',
     36.690180000, 126.576728000, @campus_place_seeded_at, @campus_place_seeded_at),
    (12, 'SEOSAN', '바니스피자', '바니스피자',
     36.689970000, 126.577191000, @campus_place_seeded_at, @campus_place_seeded_at),
    (13, 'SEOSAN', '호랑', '호랑',
     36.689997000, 126.577296000, @campus_place_seeded_at, @campus_place_seeded_at),
    (14, 'SEOSAN', '38고기', '38고기',
     36.690259000, 126.577179000, @campus_place_seeded_at, @campus_place_seeded_at),
    (15, 'SEOSAN', 'xoxo', 'XOXO',
     36.690303000, 126.577189000, @campus_place_seeded_at, @campus_place_seeded_at),
    (16, 'SEOSAN', '38고기 옆', '38고기옆',
     36.690235000, 126.577113000, @campus_place_seeded_at, @campus_place_seeded_at),
    (17, 'SEOSAN', '대곡리포장마차', '대곡리포장마차',
     36.690379000, 126.577525000, @campus_place_seeded_at, @campus_place_seeded_at),
    (18, 'SEOSAN', '먹통', '먹통',
     36.690406000, 126.577661000, @campus_place_seeded_at, @campus_place_seeded_at),
    (19, 'SEOSAN', '대정문 세븐일레븐', '대정문세븐일레븐',
     36.692093000, 126.574737000, @campus_place_seeded_at, @campus_place_seeded_at),
    (20, 'SEOSAN', '대정문 GS', '대정문GS',
     36.690226000, 126.576512000, @campus_place_seeded_at, @campus_place_seeded_at),
    (21, 'SEOSAN', '대정문 버스정류장 서울방향', '대정문버스정류장서울방향',
     36.690270000, 126.575981000, @campus_place_seeded_at, @campus_place_seeded_at),
    (22, 'SEOSAN', '대정문 CU', '대정문CU',
     36.689474000, 126.576247000, @campus_place_seeded_at, @campus_place_seeded_at),
    (23, 'SEOSAN', '대정문 버스정류장 홍성방향', '대정문버스정류장홍성방향',
     36.689448000, 126.576381000, @campus_place_seeded_at, @campus_place_seeded_at),
    (24, 'SEOSAN', 'BBQ', 'BBQ',
     36.690105000, 126.578093000, @campus_place_seeded_at, @campus_place_seeded_at),
    (25, 'SEOSAN', '정문CU', '정문CU',
     36.690534000, 126.577811000, @campus_place_seeded_at, @campus_place_seeded_at),
    (26, 'SEOSAN', '헤커PC', '헤커PC',
     36.690476000, 126.577860000, @campus_place_seeded_at, @campus_place_seeded_at),
    (27, 'SEOSAN', '워시엔조이', '워시엔조이',
     36.690595000, 126.577723000, @campus_place_seeded_at, @campus_place_seeded_at),
    (28, 'SEOSAN', '최초장집', '최초장집',
     36.690488000, 126.578154000, @campus_place_seeded_at, @campus_place_seeded_at),
    (29, 'SEOSAN', '이모네', '이모네',
     36.690517000, 126.578243000, @campus_place_seeded_at, @campus_place_seeded_at),
    (30, 'SEOSAN', '투다리', '투다리',
     36.690541000, 126.578415000, @campus_place_seeded_at, @campus_place_seeded_at),
    (31, 'SEOSAN', '먹거리대장', '먹거리대장',
     36.690556000, 126.578538000, @campus_place_seeded_at, @campus_place_seeded_at),
    (32, 'SEOSAN', '음주다방', '음주다방',
     36.690598000, 126.578461000, @campus_place_seeded_at, @campus_place_seeded_at),
    (33, 'SEOSAN', '빠사시', '빠사시',
     36.690572000, 126.578697000, @campus_place_seeded_at, @campus_place_seeded_at),
    (34, 'SEOSAN', '파티', '파티',
     36.690591000, 126.578831000, @campus_place_seeded_at, @campus_place_seeded_at),
    (35, 'SEOSAN', '제이제이당구', '제이제이당구',
     36.690668000, 126.579218000, @campus_place_seeded_at, @campus_place_seeded_at),
    (36, 'SEOSAN', '콩닭콩닭', '콩닭콩닭',
     36.690343000, 126.579256000, @campus_place_seeded_at, @campus_place_seeded_at),
    (37, 'SEOSAN', '맘스터치', '맘스터치',
     36.690357000, 126.579381000, @campus_place_seeded_at, @campus_place_seeded_at),
    (38, 'SEOSAN', '그리스', '그리스',
     36.690271000, 126.579340000, @campus_place_seeded_at, @campus_place_seeded_at),
    (39, 'SEOSAN', '대학복사', '대학복사',
     36.690476000, 126.579618000, @campus_place_seeded_at, @campus_place_seeded_at),
    (40, 'SEOSAN', '메가', '메가',
     36.690543000, 126.579795000, @campus_place_seeded_at, @campus_place_seeded_at),
    (41, 'SEOSAN', '엄청난파닭', '엄청난파닭',
     36.690598000, 126.580053000, @campus_place_seeded_at, @campus_place_seeded_at),
    (42, 'SEOSAN', '이마트24 정문', '이마트24정문',
     36.690632000, 126.580182000, @campus_place_seeded_at, @campus_place_seeded_at),
    (43, 'SEOSAN', '무인탁구', '무인탁구',
     36.690594000, 126.580193000, @campus_place_seeded_at, @campus_place_seeded_at),
    (44, 'SEOSAN', '공차', '공차',
     36.690607000, 126.580324000, @campus_place_seeded_at, @campus_place_seeded_at),
    (45, 'SEOSAN', '감동까스/59쌀피자', '감동까스/59쌀피자',
     36.690919000, 126.580052000, @campus_place_seeded_at, @campus_place_seeded_at),
    (46, 'SEOSAN', '엽떡', '엽떡',
     36.690848000, 126.580258000, @campus_place_seeded_at, @campus_place_seeded_at),
    (47, 'SEOSAN', '세이커피', '세이커피',
     36.690871000, 126.580333000, @campus_place_seeded_at, @campus_place_seeded_at),
    (48, 'SEOSAN', '정문GS', '정문GS',
     36.690931000, 126.580491000, @campus_place_seeded_at, @campus_place_seeded_at),
    (49, 'SEOSAN', '스피드카피', '스피드카피',
     36.690980000, 126.580660000, @campus_place_seeded_at, @campus_place_seeded_at),
    (50, 'SEOSAN', '주당', '주당',
     36.690100000, 126.580493000, @campus_place_seeded_at, @campus_place_seeded_at),
    (51, 'SEOSAN', '86PC', '86PC',
     36.691048000, 126.580612000, @campus_place_seeded_at, @campus_place_seeded_at),
    (52, 'SEOSAN', '정문 코인노래방', '정문코인노래방',
     36.690992000, 126.580634000, @campus_place_seeded_at, @campus_place_seeded_at),
    (53, 'SEOSAN', '토프레소', '토프레소',
     36.691065000, 126.580743000, @campus_place_seeded_at, @campus_place_seeded_at),
    (54, 'SEOSAN', '정문버스정류장', '정문버스정류장',
     36.690638000, 126.580530000, @campus_place_seeded_at, @campus_place_seeded_at),
    (55, 'SEOSAN', '이마트24 재민이형집쪽', '이마트24재민이형집쪽',
     36.691145000, 126.579770000, @campus_place_seeded_at, @campus_place_seeded_at),
    (56, 'SEOSAN', '도스마스', '도스마스',
     36.691151000, 126.579935000, @campus_place_seeded_at, @campus_place_seeded_at),
    (57, 'SEOSAN', '더테이블', '더테이블',
     36.691291000, 126.579982000, @campus_place_seeded_at, @campus_place_seeded_at),
    (58, 'SEOSAN', '아이스크림할인점', '아이스크림할인점',
     36.691293000, 126.580028000, @campus_place_seeded_at, @campus_place_seeded_at),
    (59, 'SEOSAN', '마라탕집', '마라탕집',
     36.691143000, 126.580095000, @campus_place_seeded_at, @campus_place_seeded_at),
    (60, 'SEOSAN', '나사', '나사',
     36.691370000, 126.580412000, @campus_place_seeded_at, @campus_place_seeded_at),
    (61, 'SEOSAN', '더큰', '더큰',
     36.691243000, 126.580607000, @campus_place_seeded_at, @campus_place_seeded_at),
    (62, 'SEOSAN', '이삭토스트', '이삭토스트',
     36.691234000, 126.580741000, @campus_place_seeded_at, @campus_place_seeded_at),
    (63, 'SEOSAN', '하이마트', '하이마트',
     36.691215000, 126.580907000, @campus_place_seeded_at, @campus_place_seeded_at),
    (64, 'SEOSAN', '에잇어클락', '에잇어클락',
     36.691211000, 126.581088000, @campus_place_seeded_at, @campus_place_seeded_at),
    (65, 'SEOSAN', '이것이국밥이다', '이것이국밥이다',
     36.691231000, 126.581179000, @campus_place_seeded_at, @campus_place_seeded_at),
    (66, 'SEOSAN', '학사반점', '학사반점',
     36.691269000, 126.581438000, @campus_place_seeded_at, @campus_place_seeded_at),
    (67, 'SEOSAN', '후문CU', '후문CU',
     36.692174000, 126.583623000, @campus_place_seeded_at, @campus_place_seeded_at),
    (68, 'SEOSAN', '영춘원', '영춘원',
     36.692208000, 126.583662000, @campus_place_seeded_at, @campus_place_seeded_at),
    (69, 'SEOSAN', '잇또라멘', '잇또라멘',
     36.692799000, 126.585949000, @campus_place_seeded_at, @campus_place_seeded_at),
    (70, 'SEOSAN', '킹코인', '킹코인',
     36.692819000, 126.586027000, @campus_place_seeded_at, @campus_place_seeded_at),
    (71, 'SEOSAN', '후문GS', '후문GS',
     36.692676000, 126.586133000, @campus_place_seeded_at, @campus_place_seeded_at),
    (72, 'SEOSAN', '스타PC', '스타PC',
     36.692662000, 126.585986000, @campus_place_seeded_at, @campus_place_seeded_at),
    (73, 'SEOSAN', '봉주르대곡리', '봉주르대곡리',
     36.692632000, 126.586513000, @campus_place_seeded_at, @campus_place_seeded_at),
    (74, 'SEOSAN', '한뚝배기', '한뚝배기',
     36.692788000, 126.587084000, @campus_place_seeded_at, @campus_place_seeded_at),
    (75, 'SEOSAN', '해미막국수', '해미막국수',
     36.693140000, 126.587067000, @campus_place_seeded_at, @campus_place_seeded_at),
    (76, 'SEOSAN', '이학관/카페드림', '이학관/카페드림',
     36.690669000, 126.581760000, @campus_place_seeded_at, @campus_place_seeded_at),
    (77, 'SEOSAN', '보건관', '보건관',
     36.690237000, 126.581944000, @campus_place_seeded_at, @campus_place_seeded_at),
    (78, 'SEOSAN', '여긱', '여긱',
     36.689868000, 126.582259000, @campus_place_seeded_at, @campus_place_seeded_at),
    (79, 'SEOSAN', '영암관', '영암관',
     36.691341000, 126.582453000, @campus_place_seeded_at, @campus_place_seeded_at),
    (80, 'SEOSAN', '학생회관', '학생회관',
     36.691486000, 126.583172000, @campus_place_seeded_at, @campus_place_seeded_at),
    (81, 'SEOSAN', '건축관', '건축관',
     36.691361000, 126.583607000, @campus_place_seeded_at, @campus_place_seeded_at),
    (82, 'SEOSAN', '도서관', '도서관',
     36.690021000, 126.584036000, @campus_place_seeded_at, @campus_place_seeded_at),
    (83, 'SEOSAN', '인곡관', '인곡관',
     36.691789000, 126.584722000, @campus_place_seeded_at, @campus_place_seeded_at),
    (84, 'SEOSAN', '학군단', '학군단',
     36.690958000, 126.585458000, @campus_place_seeded_at, @campus_place_seeded_at),
    (85, 'SEOSAN', '인문사회관', '인문사회관',
     36.690100000, 126.585907000, @campus_place_seeded_at, @campus_place_seeded_at),
    (86, 'SEOSAN', '공학관', '공학관',
     36.690884000, 126.585761000, @campus_place_seeded_at, @campus_place_seeded_at),
    (87, 'SEOSAN', '상상공작소', '상상공작소',
     36.691625000, 126.586317000, @campus_place_seeded_at, @campus_place_seeded_at),
    (88, 'SEOSAN', '예술관', '예술관',
     36.689406000, 126.587976000, @campus_place_seeded_at, @campus_place_seeded_at),
    (89, 'SEOSAN', '대운동장', '대운동장',
     36.690743000, 126.588396000, @campus_place_seeded_at, @campus_place_seeded_at),
    (90, 'SEOSAN', '영암체육관', '영암체육관',
     36.691580000, 126.588189000, @campus_place_seeded_at, @campus_place_seeded_at),
    (91, 'SEOSAN', '자악관', '자악관',
     36.691490000, 126.588935000, @campus_place_seeded_at, @campus_place_seeded_at),
    (92, 'SEOSAN', '농구장', '농구장',
     36.692188000, 126.586706000, @campus_place_seeded_at, @campus_place_seeded_at),
    (93, 'SEOSAN', '테니스장', '테니스장',
     36.691951000, 126.586864000, @campus_place_seeded_at, @campus_place_seeded_at),
    (94, 'SEOSAN', '이학관 전기차충전소', '이학관전기차충전소',
     36.690548000, 126.581160000, @campus_place_seeded_at, @campus_place_seeded_at),
    (95, 'SEOSAN', '연암도서관 전기차충전소', '연암도서관전기차충전소',
     36.690061000, 126.583477000, @campus_place_seeded_at, @campus_place_seeded_at),
    (96, 'SEOSAN', '빵곡관', '빵곡관',
     36.691897000, 126.584103000, @campus_place_seeded_at, @campus_place_seeded_at),
    (97, 'SEOSAN', '서점', '서점',
     36.691887000, 126.584237000, @campus_place_seeded_at, @campus_place_seeded_at),
    (98, 'TAEAN', '항공기술교육원', '항공기술교육원',
     36.596492000, 126.292215000, @campus_place_seeded_at, @campus_place_seeded_at),
    (99, 'TAEAN', '창업2관', '창업2관',
     36.595669000, 126.293328000, @campus_place_seeded_at, @campus_place_seeded_at),
    (100, 'TAEAN', '태안본관', '태안본관',
     36.594581000, 126.294056000, @campus_place_seeded_at, @campus_place_seeded_at),
    (101, 'TAEAN', '실습2동', '실습2동',
     36.593520000, 126.294879000, @campus_place_seeded_at, @campus_place_seeded_at),
    (102, 'TAEAN', '창업3관', '창업3관',
     36.592307000, 126.295738000, @campus_place_seeded_at, @campus_place_seeded_at),
    (103, 'TAEAN', '태안기숙사구관', '태안기숙사구관',
     36.594001000, 126.292797000, @campus_place_seeded_at, @campus_place_seeded_at),
    (104, 'TAEAN', '태안기숙사신관', '태안기숙사신관',
     36.593186000, 126.293383000, @campus_place_seeded_at, @campus_place_seeded_at),
    (105, 'TAEAN', '기숙사CU', '기숙사CU',
     36.593503000, 126.293142000, @campus_place_seeded_at, @campus_place_seeded_at),
    (106, 'TAEAN', '태안세븐일레븐', '태안세븐일레븐',
     36.594412000, 126.292178000, @campus_place_seeded_at, @campus_place_seeded_at),
    (107, 'TAEAN', '태안GS25', '태안GS25',
     36.595482000, 126.291524000, @campus_place_seeded_at, @campus_place_seeded_at),
    (108, 'TAEAN', '태안코노', '태안코노',
     36.596202000, 126.290875000, @campus_place_seeded_at, @campus_place_seeded_at),
    (109, 'TAEAN', '피굽남', '피굽남',
     36.596110000, 126.290957000, @campus_place_seeded_at, @campus_place_seeded_at),
    (110, 'TAEAN', '막리단길', '막리단길',
     36.596069000, 126.290991000, @campus_place_seeded_at, @campus_place_seeded_at),
    (111, 'TAEAN', '경아두마리치킨', '경아두마리치킨',
     36.595994000, 126.291036000, @campus_place_seeded_at, @campus_place_seeded_at),
    (112, 'TAEAN', '태안GS25 뒤쪽', '태안GS25뒤쪽',
     36.590894000, 126.295726000, @campus_place_seeded_at, @campus_place_seeded_at),
    (113, 'TAEAN', '태안캠해양교육원', '태안캠해양교육원',
     36.593851000, 126.300619000, @campus_place_seeded_at, @campus_place_seeded_at),
    (114, 'TAEAN', '태안마슬랜', '태안마슬랜',
     36.590423000, 126.295288000, @campus_place_seeded_at, @campus_place_seeded_at);

COMMIT;


CREATE TABLE course_import_histories (
    id BINARY(16) NOT NULL,
    import_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    successful_dedup_key VARCHAR(255) NULL,
    file_name VARCHAR(500) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    parser_version VARCHAR(100) NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    storage_status VARCHAR(30) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    offering_count INT NOT NULL,
    raw_payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_import_id UNIQUE (import_id),
    CONSTRAINT uk_course_import_success_dedup UNIQUE (successful_dedup_key),
    INDEX ix_course_import_scope (academic_year, semester, curriculum_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE semester_academic_units (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    academic_unit_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_unit_curriculum
        UNIQUE (semester_id, academic_unit_id, curriculum_type),
    INDEX ix_semester_unit_scope (semester_id, curriculum_type),
    CONSTRAINT fk_semester_unit_semester
        FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_semester_unit_master
        FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 학기별 매핑 UUID는 사용자 시간표가 참조하므로 재수입 시 삭제하지 않고 재사용한다.
CREATE TABLE course_offerings (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    active BIT(1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_semester_course UNIQUE (semester_id, course_id),
    INDEX ix_offering_scope (semester_id, curriculum_type, active),
    INDEX ix_offering_import (import_history_id),
    CONSTRAINT fk_offering_semester
        FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_offering_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_offering_import
        FOREIGN KEY (import_history_id) REFERENCES course_import_histories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_general_education (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    classification VARCHAR(30) NOT NULL,
    classification_name VARCHAR(255) NULL,
    category_code VARCHAR(100) NULL,
    category_name VARCHAR(255) NULL,
    area VARCHAR(30) NULL,
    delivery_provider VARCHAR(50) NOT NULL,
    delivery_provider_name VARCHAR(255) NULL,
    source_path_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_general_context_course UNIQUE (course_id),
    INDEX ix_general_context_filter (classification, area, delivery_provider),
    CONSTRAINT fk_general_context_course
        FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_allowed_grades (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    grade INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_allowed_grade UNIQUE (course_id, grade),
    CONSTRAINT fk_allowed_grade_course
        FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_eligible_departments (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_eligible_department UNIQUE (course_id, department_name),
    CONSTRAINT fk_eligible_department_course
        FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_schedules (
    id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    schedule_order INT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    periods_value VARCHAR(200) NOT NULL,
    classroom_id BINARY(16) NULL,
    PRIMARY KEY (id),
    INDEX ix_schedule_course_order (course_id, schedule_order),
    CONSTRAINT fk_schedule_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_schedule_classroom
        FOREIGN KEY (classroom_id) REFERENCES classrooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE timetable_courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    timetable_id BIGINT NOT NULL,
    course_offering_id BINARY(16) NOT NULL,
    expected_grade VARCHAR(20) NULL,
    custom_course_name VARCHAR(255) NULL,
    custom_credit DECIMAL(8,3) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_timetable_course_offering
        UNIQUE (timetable_id, course_offering_id),
    INDEX ix_timetable_course_timetable (timetable_id),
    INDEX ix_timetable_course_offering (course_offering_id),
    CONSTRAINT ck_timetable_course_custom_name CHECK (
        custom_course_name IS NULL
        OR CHAR_LENGTH(TRIM(custom_course_name)) > 0
    ),
    CONSTRAINT ck_timetable_course_custom_credit CHECK (
        custom_credit IS NULL
        OR (custom_credit >= 0.001 AND custom_credit <= 20.000)
    ),
    CONSTRAINT ck_timetable_course_expected_grade CHECK (
        expected_grade IS NULL
        OR BINARY expected_grade IN (
            BINARY 'A_PLUS',
            BINARY 'A',
            BINARY 'B_PLUS',
            BINARY 'B',
            BINARY 'C_PLUS',
            BINARY 'C',
            BINARY 'D_PLUS',
            BINARY 'D',
            BINARY 'P',
            BINARY 'F'
        )
    ),
    CONSTRAINT fk_timetable_course_timetable
        FOREIGN KEY (timetable_id) REFERENCES timetables (id) ON DELETE CASCADE,
    CONSTRAINT fk_timetable_course_offering
        FOREIGN KEY (course_offering_id) REFERENCES course_offerings (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_source_cells (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    column_index INT NOT NULL,
    header_name VARCHAR(500) NOT NULL,
    canonical_field VARCHAR(100) NULL,
    cell_value LONGTEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_source_column UNIQUE (offering_id, column_index),
    CONSTRAINT fk_source_cell_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE semester_general_category_nodes (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    node_key VARCHAR(255) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    code VARCHAR(100) NULL,
    name VARCHAR(500) NOT NULL,
    parent_key VARCHAR(255) NULL,
    classification VARCHAR(30) NULL,
    classification_name VARCHAR(255) NULL,
    area VARCHAR(30) NULL,
    delivery_provider VARCHAR(50) NULL,
    delivery_provider_name VARCHAR(255) NULL,
    source_path_json LONGTEXT NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_curriculum_node
        UNIQUE (semester_id, curriculum_type, node_key),
    INDEX ix_general_node_parent (semester_id, curriculum_type, parent_key),
    INDEX ix_general_node_order (semester_id, curriculum_type, sort_order),
    CONSTRAINT fk_general_node_semester
        FOREIGN KEY (semester_id) REFERENCES semesters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_import_issues (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    code VARCHAR(100) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    sheet_name VARCHAR(255) NULL,
    issue_row_number INT NULL,
    field_name VARCHAR(100) NULL,
    raw_value VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    INDEX ix_import_issue_history (import_history_id),
    CONSTRAINT fk_import_issue_history
        FOREIGN KEY (import_history_id) REFERENCES course_import_histories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equivalent_course_import_histories (
    id BINARY(16) NOT NULL,
    import_id VARCHAR(100) NOT NULL,
    active_scope_key VARCHAR(30) NULL,
    canonical_hash VARCHAR(64) NOT NULL,
    raw_file_sha256 VARCHAR(64) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    parser_version VARCHAR(100) NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    history_status VARCHAR(30) NOT NULL,
    group_count INT NOT NULL,
    member_count INT NOT NULL,
    raw_payload_json LONGTEXT NOT NULL,
    raw_issues_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equivalent_import_id UNIQUE (import_id),
    CONSTRAINT uk_equivalent_active_scope UNIQUE (active_scope_key),
    INDEX ix_equivalent_import_scope (academic_year, semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equivalent_course_groups (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    source_serial INT NOT NULL,
    group_order INT NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_start_row INT NOT NULL,
    source_end_row INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equivalent_group_serial
        UNIQUE (import_history_id, source_serial),
    CONSTRAINT uk_equivalent_group_order
        UNIQUE (import_history_id, group_order),
    INDEX ix_equivalent_group_history (import_history_id),
    CONSTRAINT fk_equivalent_group_history
        FOREIGN KEY (import_history_id)
        REFERENCES equivalent_course_import_histories (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE equivalent_course_members (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    group_id BINARY(16) NOT NULL,
    course_code VARCHAR(7) NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    member_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equivalent_member_code
        UNIQUE (import_history_id, course_code),
    INDEX ix_equivalent_member_group_order (group_id, member_order),
    CONSTRAINT fk_equivalent_member_history
        FOREIGN KEY (import_history_id)
        REFERENCES equivalent_course_import_histories (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_equivalent_member_group
        FOREIGN KEY (group_id)
        REFERENCES equivalent_course_groups (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cross_major_recognition_import_histories (
    id BINARY(16) NOT NULL,
    policy_year INT NOT NULL,
    uploaded_semester INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    active_scope_key VARCHAR(100) NULL,
    file_name VARCHAR(500) NOT NULL,
    raw_file_sha256 VARCHAR(64) NOT NULL,
    canonical_data_sha256 VARCHAR(64) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    raw_row_count INT NOT NULL,
    rule_count INT NOT NULL,
    warning_count INT NOT NULL,
    issues_json LONGTEXT NOT NULL,
    raw_payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cross_major_active_scope UNIQUE (active_scope_key),
    INDEX ix_cross_major_import_policy_year (policy_year, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cross_major_recognition_rules (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    rule_key VARCHAR(64) NOT NULL,
    student_college_name VARCHAR(255) NOT NULL,
    student_department_name VARCHAR(255) NOT NULL,
    student_major_name VARCHAR(255) NOT NULL,
    offering_college_name VARCHAR(255) NOT NULL,
    offering_department_name VARCHAR(255) NOT NULL,
    offering_major_name VARCHAR(255) NOT NULL,
    offering_department_key VARCHAR(255) NOT NULL,
    offering_major_key VARCHAR(255) NOT NULL,
    course_code VARCHAR(7) NOT NULL,
    course_name_snapshot VARCHAR(255) NOT NULL,
    course_name_key VARCHAR(255) NOT NULL,
    effective_year INT NOT NULL,
    effective_semester INT NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cross_major_rule_history_key
        UNIQUE (import_history_id, rule_key),
    INDEX ix_cross_major_rule_history_code (import_history_id, course_code),
    CONSTRAINT fk_cross_major_rule_history
        FOREIGN KEY (import_history_id)
        REFERENCES cross_major_recognition_import_histories (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE clubs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(30) NOT NULL,
    profile_image_url VARCHAR(2048) NULL,
    background_image_url VARCHAR(2048) NULL,
    short_description VARCHAR(255) NULL,
    introduction LONGTEXT NULL,
    activity_content LONGTEXT NULL,
    recruitment_content LONGTEXT NULL,
    instagram_url VARCHAR(2048) NULL,
    kakao_talk_url VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_clubs_name UNIQUE (name),
    INDEX idx_clubs_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE club_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    liker_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_club_likes_club_liker UNIQUE (club_id, liker_id),
    INDEX idx_club_likes_club (club_id),
    INDEX idx_club_likes_liker (liker_id),
    CONSTRAINT fk_club_likes_club
        FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE,
    CONSTRAINT fk_club_likes_liker
        FOREIGN KEY (liker_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE club_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_club_reviews_club_reviewer UNIQUE (club_id, reviewer_id),
    INDEX idx_club_reviews_club (club_id),
    INDEX idx_club_reviews_reviewer (reviewer_id),
    CONSTRAINT fk_club_reviews_club
        FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE,
    CONSTRAINT fk_club_reviews_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE club_review_selections (
    club_review_id BIGINT NOT NULL,
    review_option VARCHAR(50) NOT NULL,
    CONSTRAINT uk_club_review_selections_review_option
        UNIQUE (club_review_id, review_option),
    INDEX idx_club_review_selections_option (review_option),
    CONSTRAINT fk_club_review_selections_review
        FOREIGN KEY (club_review_id) REFERENCES club_reviews (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
