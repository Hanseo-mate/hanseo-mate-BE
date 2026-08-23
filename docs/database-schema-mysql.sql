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
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_timetable_course_offering
        UNIQUE (timetable_id, course_offering_id),
    INDEX ix_timetable_course_timetable (timetable_id),
    INDEX ix_timetable_course_offering (course_offering_id),
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
