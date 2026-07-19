-- HanseoMate 운영 MySQL 스키마
-- Flyway를 사용하지 않으므로 운영 배포 전에 DBA 또는 배포 절차에서 1회 실행한다.
-- MySQL 8.0 / utf8mb4 기준

CREATE TABLE IF NOT EXISTS essential_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    category VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS semesters (
    id BINARY(16) NOT NULL,
    academic_year INT NOT NULL,
    semester INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_year_term UNIQUE (academic_year, semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS academic_units (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    major_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_unit_master_key UNIQUE (master_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS courses (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    course_code VARCHAR(100) NULL,
    course_name VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_master_key UNIQUE (master_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS classrooms (
    id BINARY(16) NOT NULL,
    master_key VARCHAR(64) NOT NULL,
    campus_code VARCHAR(100) NULL,
    building_name VARCHAR(255) NULL,
    room_number VARCHAR(100) NULL,
    original_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_classroom_master_key UNIQUE (master_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_import_histories (
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

CREATE TABLE IF NOT EXISTS semester_academic_units (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    academic_unit_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_semester_unit_curriculum
        UNIQUE (semester_id, academic_unit_id, curriculum_type),
    CONSTRAINT fk_semester_unit_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_semester_unit_master FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_offerings (
    id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    course_id BINARY(16) NOT NULL,
    academic_unit_id BINARY(16) NULL,
    import_history_id BINARY(16) NOT NULL,
    curriculum_type VARCHAR(30) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    course_code_snapshot VARCHAR(100) NULL,
    course_name_snapshot VARCHAR(255) NULL,
    section_no VARCHAR(100) NULL,
    credit DECIMAL(8,3) NULL,
    class_hours DECIMAL(8,3) NULL,
    instructor_name VARCHAR(255) NULL,
    target_grade INT NULL,
    common_grade TINYINT(1) NOT NULL,
    team_teaching TINYINT(1) NULL,
    note VARCHAR(2000) NULL,
    eligibility_note VARCHAR(2000) NULL,
    schedule_text VARCHAR(2000) NULL,
    classroom_text VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_offering_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    CONSTRAINT fk_offering_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_offering_academic_unit FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id),
    CONSTRAINT fk_offering_import FOREIGN KEY (import_history_id) REFERENCES course_import_histories (id),
    INDEX ix_offering_scope (semester_id, curriculum_type),
    INDEX ix_offering_course_name (course_name_snapshot),
    INDEX ix_offering_instructor (instructor_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS offering_general_education (
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
    CONSTRAINT fk_general_context_offering FOREIGN KEY (offering_id) REFERENCES course_offerings (id),
    INDEX ix_general_context_filter (classification, area, delivery_provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS offering_allowed_grades (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    grade INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_allowed_grade UNIQUE (offering_id, grade),
    CONSTRAINT fk_allowed_grade_offering FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS offering_eligible_departments (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    department_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_eligible_department UNIQUE (offering_id, department_name),
    CONSTRAINT fk_eligible_department_offering FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_schedules (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    classroom_id BINARY(16) NULL,
    schedule_order INT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    periods_value VARCHAR(200) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_offering FOREIGN KEY (offering_id) REFERENCES course_offerings (id),
    CONSTRAINT fk_schedule_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
    INDEX ix_schedule_offering_order (offering_id, schedule_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_source_cells (
    id BINARY(16) NOT NULL,
    offering_id BINARY(16) NOT NULL,
    column_index INT NOT NULL,
    header_name VARCHAR(500) NOT NULL,
    canonical_field VARCHAR(100) NULL,
    cell_value LONGTEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_offering_source_column UNIQUE (offering_id, column_index),
    CONSTRAINT fk_source_cell_offering FOREIGN KEY (offering_id) REFERENCES course_offerings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS semester_general_category_nodes (
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
    CONSTRAINT uk_semester_curriculum_node UNIQUE (semester_id, curriculum_type, node_key),
    CONSTRAINT fk_general_node_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
    INDEX ix_general_node_parent (semester_id, curriculum_type, parent_key),
    INDEX ix_general_node_order (semester_id, curriculum_type, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_import_issues (
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
    CONSTRAINT fk_import_issue_history FOREIGN KEY (import_history_id) REFERENCES course_import_histories (id),
    INDEX ix_import_issue_history (import_history_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 DB에서 raw_payload_json이 TEXT 등으로 생성된 경우에도 대용량 JSON을 저장할 수 있게 보정한다.
ALTER TABLE course_import_histories
    MODIFY COLUMN raw_payload_json LONGTEXT NOT NULL;

-- Existing databases may still have cell_value as VARCHAR or TEXT.
ALTER TABLE course_source_cells
    MODIFY COLUMN cell_value LONGTEXT NULL;
