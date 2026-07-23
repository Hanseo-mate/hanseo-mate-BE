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

CREATE TABLE semesters (
    id BINARY(16) NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_year_term UNIQUE (academic_year, semester)
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

CREATE TABLE courses (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    course_code VARCHAR(100) NULL,
    course_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_master_key UNIQUE (master_key)
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

CREATE TABLE course_offerings (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    academic_unit_id BINARY(16) NULL,
    import_history_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    section_no VARCHAR(100) NULL,
    course_code_snapshot VARCHAR(100) NULL,
    course_name_snapshot VARCHAR(255) NULL,
    credit DECIMAL(8,3) NULL,
    class_hours DECIMAL(8,3) NULL,
    instructor_name VARCHAR(255) NULL,
    target_grade INT NULL,
    common_grade BIT(1) NOT NULL,
    team_teaching BIT(1) NULL,
    note VARCHAR(2000) NULL,
    eligibility_note VARCHAR(2000) NULL,
    schedule_text VARCHAR(2000) NULL,
    classroom_text VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    INDEX ix_offering_scope (semester_id, curriculum_type),
    INDEX ix_offering_course_name (course_name_snapshot),
    INDEX ix_offering_instructor (instructor_name),
    CONSTRAINT fk_offering_semester
        FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_offering_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_offering_academic_unit
        FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id),
    CONSTRAINT fk_offering_import
        FOREIGN KEY (import_history_id) REFERENCES course_import_histories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_general_education (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    classification VARCHAR(30) NOT NULL,
    classification_name VARCHAR(255) NULL,
    category_code VARCHAR(100) NULL,
    category_name VARCHAR(255) NULL,
    area VARCHAR(30) NULL,
    delivery_provider VARCHAR(50) NOT NULL,
    delivery_provider_name VARCHAR(255) NULL,
    source_path_json LONGTEXT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_general_context_offering UNIQUE (offering_id),
    INDEX ix_general_context_filter (classification, area, delivery_provider),
    CONSTRAINT fk_general_context_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_allowed_grades (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    grade INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_allowed_grade UNIQUE (offering_id, grade),
    CONSTRAINT fk_allowed_grade_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE offering_eligible_departments (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_eligible_department UNIQUE (offering_id, department_name),
    CONSTRAINT fk_eligible_department_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE course_schedules (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    schedule_order INT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    periods_value VARCHAR(200) NOT NULL,
    classroom_id BINARY(16) NULL,
    PRIMARY KEY (id),
    INDEX ix_schedule_offering_order (offering_id, schedule_order),
    CONSTRAINT fk_schedule_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings (id),
    CONSTRAINT fk_schedule_classroom
        FOREIGN KEY (classroom_id) REFERENCES classrooms (id)
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
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_club_likes_club (club_id),
    CONSTRAINT fk_club_likes_club
        FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE club_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    club_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_club_reviews_club (club_id),
    CONSTRAINT fk_club_reviews_club
        FOREIGN KEY (club_id) REFERENCES clubs (id) ON DELETE CASCADE
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
