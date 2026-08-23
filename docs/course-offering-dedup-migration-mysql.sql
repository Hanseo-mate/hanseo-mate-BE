-- 기존 운영 DB의 학기별 강좌 데이터를 공통 Course + 학기별 Offering 매핑으로 전환합니다.
-- 대상: MySQL 8.0, docs/database-schema-mysql.sql의 변경 전 강좌 테이블 구조
-- 주의: 이 스크립트는 한 번만 실행하며, 빈 DB에는 실행하지 않습니다.
-- 주의: MySQL DDL은 암시적으로 commit됩니다. 애플리케이션을 중지하고 백업한 뒤 실행하세요.
-- 상세 절차와 복구 원칙은 docs/course-offering-dedup-migration.md를 먼저 확인하세요.

SET NAMES utf8mb4;

SET @course_count_before = (SELECT COUNT(*) FROM courses);
SET @offering_count_before = (SELECT COUNT(*) FROM course_offerings);
SET @timetable_course_count_before = (SELECT COUNT(*) FROM timetable_courses);
SET @source_cell_count_before = (SELECT COUNT(*) FROM course_source_cells);

-- 변경 전 구조와 안전 전제조건을 확인합니다. 하나라도 맞지 않으면 실제 DDL 전에 중단합니다.
DELIMITER //
CREATE PROCEDURE assert_course_dedup_migration_ready()
BEGIN
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Select the target database before running the course migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT 'course_offerings' AS table_name, 'academic_unit_id' AS column_name
            UNION ALL SELECT 'course_offerings', 'course_code_snapshot'
            UNION ALL SELECT 'course_offerings', 'course_name_snapshot'
            UNION ALL SELECT 'course_offerings', 'section_no'
            UNION ALL SELECT 'course_offerings', 'credit'
            UNION ALL SELECT 'course_offerings', 'class_hours'
            UNION ALL SELECT 'course_offerings', 'instructor_name'
            UNION ALL SELECT 'course_offerings', 'target_grade'
            UNION ALL SELECT 'course_offerings', 'common_grade'
            UNION ALL SELECT 'course_offerings', 'team_teaching'
            UNION ALL SELECT 'course_offerings', 'note'
            UNION ALL SELECT 'course_offerings', 'eligibility_note'
            UNION ALL SELECT 'course_offerings', 'schedule_text'
            UNION ALL SELECT 'course_offerings', 'classroom_text'
            UNION ALL SELECT 'offering_general_education', 'offering_id'
            UNION ALL SELECT 'offering_allowed_grades', 'offering_id'
            UNION ALL SELECT 'offering_eligible_departments', 'offering_id'
            UNION ALL SELECT 'course_schedules', 'offering_id'
            UNION ALL SELECT 'timetable_courses', 'id'
            UNION ALL SELECT 'timetable_courses', 'timetable_id'
            UNION ALL SELECT 'timetable_courses', 'course_offering_id'
            UNION ALL SELECT 'timetable_courses', 'created_at'
            UNION ALL SELECT 'course_source_cells', 'id'
            UNION ALL SELECT 'course_source_cells', 'offering_id'
            UNION ALL SELECT 'course_source_cells', 'column_index'
        ) expected
        LEFT JOIN information_schema.columns actual
          ON actual.table_schema = DATABASE()
         AND actual.table_name = expected.table_name
         AND actual.column_name = expected.column_name
        WHERE actual.column_name IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'One or more legacy course columns are missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND (
              (table_name = 'course_offerings' AND column_name = 'active')
              OR (table_name = 'courses' AND column_name = 'academic_unit_id')
              OR (
                  table_name IN (
                      'offering_general_education',
                      'offering_allowed_grades',
                      'offering_eligible_departments',
                      'course_schedules'
                  )
                  AND column_name = 'course_id'
              )
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'New course columns already exist; migration may be partially applied';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT 'course_offerings' AS table_name,
                   'fk_offering_academic_unit' AS constraint_name
            UNION ALL SELECT 'offering_general_education', 'fk_general_context_offering'
            UNION ALL SELECT 'offering_allowed_grades', 'fk_allowed_grade_offering'
            UNION ALL SELECT 'offering_eligible_departments', 'fk_eligible_department_offering'
            UNION ALL SELECT 'course_schedules', 'fk_schedule_offering'
            UNION ALL SELECT 'timetable_courses', 'fk_timetable_course_offering'
            UNION ALL SELECT 'course_source_cells', 'fk_source_cell_offering'
        ) expected
        LEFT JOIN information_schema.table_constraints actual
          ON actual.constraint_schema = DATABASE()
         AND actual.table_name = expected.table_name
         AND actual.constraint_name = expected.constraint_name
         AND actual.constraint_type = 'FOREIGN KEY'
        WHERE actual.constraint_name IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy foreign-key names differ from the documented schema';
    END IF;

    -- Course/Offering을 참조하는 FK의 실제 컬럼, 대상과 삭제 규칙까지 확인합니다.
    -- 문서에 없는 inbound FK가 있으면 CASCADE 손실이나 뒤늦은 RESTRICT 실패를 막기 위해 중단합니다.
    IF EXISTS (
        SELECT 1
        FROM (
            SELECT 'course_offerings' AS table_name, 'fk_offering_course' AS constraint_name,
                   'course_id' AS columns_in_order, 'courses' AS referenced_table_name,
                   'id' AS referenced_columns_in_order, 'NO ACTION' AS delete_rule
            UNION ALL SELECT 'offering_general_education', 'fk_general_context_offering',
                   'offering_id', 'course_offerings', 'id', 'NO ACTION'
            UNION ALL SELECT 'offering_allowed_grades', 'fk_allowed_grade_offering',
                   'offering_id', 'course_offerings', 'id', 'NO ACTION'
            UNION ALL SELECT 'offering_eligible_departments', 'fk_eligible_department_offering',
                   'offering_id', 'course_offerings', 'id', 'NO ACTION'
            UNION ALL SELECT 'course_schedules', 'fk_schedule_offering',
                   'offering_id', 'course_offerings', 'id', 'NO ACTION'
            UNION ALL SELECT 'timetable_courses', 'fk_timetable_course_offering',
                   'course_offering_id', 'course_offerings', 'id', 'CASCADE'
            UNION ALL SELECT 'course_source_cells', 'fk_source_cell_offering',
                   'offering_id', 'course_offerings', 'id', 'NO ACTION'
        ) expected
        LEFT JOIN (
            SELECT
                k.table_name,
                k.constraint_name,
                GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position) AS columns_in_order,
                MAX(k.referenced_table_name) AS referenced_table_name,
                GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position)
                    AS referenced_columns_in_order,
                MAX(r.delete_rule) AS delete_rule
            FROM information_schema.key_column_usage k
            JOIN information_schema.referential_constraints r
              ON r.constraint_schema = k.constraint_schema
             AND r.table_name = k.table_name
             AND r.constraint_name = k.constraint_name
            WHERE k.constraint_schema = DATABASE()
              AND k.referenced_table_schema = DATABASE()
              AND k.referenced_table_name IN ('courses', 'course_offerings')
            GROUP BY k.table_name, k.constraint_name
        ) actual
          ON actual.table_name = expected.table_name
         AND actual.constraint_name = expected.constraint_name
        WHERE actual.constraint_name IS NULL
           OR actual.columns_in_order <> expected.columns_in_order
           OR actual.referenced_table_name <> expected.referenced_table_name
           OR actual.referenced_columns_in_order <> expected.referenced_columns_in_order
           OR actual.delete_rule <> expected.delete_rule
    ) OR EXISTS (
        SELECT 1
        FROM (
            SELECT DISTINCT k.table_name, k.constraint_name
            FROM information_schema.key_column_usage k
            WHERE k.constraint_schema = DATABASE()
              AND k.referenced_table_schema = DATABASE()
              AND k.referenced_table_name IN ('courses', 'course_offerings')
        ) actual
        LEFT JOIN (
            SELECT 'course_offerings' AS table_name, 'fk_offering_course' AS constraint_name
            UNION ALL SELECT 'offering_general_education', 'fk_general_context_offering'
            UNION ALL SELECT 'offering_allowed_grades', 'fk_allowed_grade_offering'
            UNION ALL SELECT 'offering_eligible_departments', 'fk_eligible_department_offering'
            UNION ALL SELECT 'course_schedules', 'fk_schedule_offering'
            UNION ALL SELECT 'timetable_courses', 'fk_timetable_course_offering'
            UNION ALL SELECT 'course_source_cells', 'fk_source_cell_offering'
        ) expected
          ON expected.table_name = actual.table_name
         AND expected.constraint_name = actual.constraint_name
        WHERE expected.constraint_name IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Course or Offering inbound foreign keys differ from the documented schema';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT 'course_offerings' AS table_name, 'ix_offering_scope' AS index_name
            UNION ALL SELECT 'course_offerings', 'ix_offering_course_name'
            UNION ALL SELECT 'course_offerings', 'ix_offering_instructor'
            UNION ALL SELECT 'offering_general_education', 'uk_general_context_offering'
            UNION ALL SELECT 'offering_allowed_grades', 'uk_offering_allowed_grade'
            UNION ALL SELECT 'offering_eligible_departments', 'uk_offering_eligible_department'
            UNION ALL SELECT 'course_schedules', 'ix_schedule_offering_order'
            UNION ALL SELECT 'timetable_courses', 'uk_timetable_course_offering'
            UNION ALL SELECT 'course_source_cells', 'uk_offering_source_column'
        ) expected
        LEFT JOIN (
            SELECT DISTINCT table_name, index_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
        ) actual
          ON actual.table_name = expected.table_name
         AND actual.index_name = expected.index_name
        WHERE actual.index_name IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy index names differ from the documented schema';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT 'timetable_courses' AS table_name,
                   'uk_timetable_course_offering' AS index_name,
                   'timetable_id,course_offering_id' AS columns_in_order
            UNION ALL SELECT 'course_source_cells', 'uk_offering_source_column',
                   'offering_id,column_index'
        ) expected
        LEFT JOIN (
            SELECT
                table_name,
                index_name,
                MIN(non_unique) AS non_unique,
                GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name IN ('timetable_courses', 'course_source_cells')
            GROUP BY table_name, index_name
        ) actual
          ON actual.table_name = expected.table_name
         AND actual.index_name = expected.index_name
        WHERE actual.index_name IS NULL
           OR actual.non_unique <> 0
           OR actual.columns_in_order <> expected.columns_in_order
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Timetable or source-cell unique index shape differs from the documented schema';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN (
              'course_dedup_migration_ranked',
              'course_dedup_migration_map',
              'course_dedup_migration_timetable'
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Course migration work tables already exist; inspect the previous attempt';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM (
            SELECT NULLIF(UPPER(TRIM(course_code)), '') AS course_code
            FROM courses
            WHERE NULLIF(UPPER(TRIM(course_code)), '') IS NOT NULL
            GROUP BY NULLIF(UPPER(TRIM(course_code)), '')
            HAVING COUNT(*) > 1
        ) duplicate_master_codes
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate non-empty course code exists in courses';
    END IF;
END//
DELIMITER ;

CALL assert_course_dedup_migration_ready();
DROP PROCEDURE assert_course_dedup_migration_ready;

-- 과목 identity별 최초 Offering을 결정합니다.
-- 최초 기준: 수입 생성 시각 -> 원본 시트 -> 원본 행 -> Offering UUID
CREATE TABLE course_dedup_migration_ranked (
    offering_id BINARY(16) NOT NULL,
    original_course_id BINARY(16) NOT NULL,
    semester_id BINARY(16) NOT NULL,
    identity_key VARCHAR(140) NOT NULL,
    has_course_code BIT(1) NOT NULL,
    row_rank BIGINT NOT NULL,
    term_row_rank BIGINT NOT NULL,
    PRIMARY KEY (offering_id),
    INDEX ix_course_migration_identity_rank (identity_key, row_rank),
    INDEX ix_course_migration_term_rank (semester_id, identity_key, term_row_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course_dedup_migration_ranked (
    offering_id,
    original_course_id,
    semester_id,
    identity_key,
    has_course_code,
    row_rank,
    term_row_rank
)
SELECT
    ranked.offering_id,
    ranked.original_course_id,
    ranked.semester_id,
    ranked.identity_key,
    ranked.has_course_code,
    ROW_NUMBER() OVER (
        PARTITION BY ranked.identity_key
        ORDER BY ranked.imported_at, ranked.source_sheet, ranked.source_row, HEX(ranked.offering_id)
    ) AS row_rank,
    ROW_NUMBER() OVER (
        PARTITION BY ranked.semester_id, ranked.identity_key
        ORDER BY ranked.imported_at, ranked.source_sheet, ranked.source_row, HEX(ranked.offering_id)
    ) AS term_row_rank
FROM (
    SELECT
        o.id AS offering_id,
        o.course_id AS original_course_id,
        o.semester_id,
        CASE
            WHEN NULLIF(UPPER(TRIM(o.course_code_snapshot)), '') IS NULL
                THEN CONCAT('ROW|', HEX(o.id))
            ELSE CONCAT('CODE|', UPPER(TRIM(o.course_code_snapshot)))
        END AS identity_key,
        CASE
            WHEN NULLIF(UPPER(TRIM(o.course_code_snapshot)), '') IS NULL THEN b'0'
            ELSE b'1'
        END AS has_course_code,
        h.created_at AS imported_at,
        o.source_sheet,
        o.source_row
    FROM course_offerings o
    JOIN course_import_histories h ON h.id = o.import_history_id
) ranked;

CREATE TABLE course_dedup_migration_map (
    offering_id BINARY(16) NOT NULL,
    original_course_id BINARY(16) NOT NULL,
    target_course_id BINARY(16) NOT NULL,
    canonical_offering_id BINARY(16) NOT NULL,
    canonical_term_offering_id BINARY(16) NOT NULL,
    has_course_code BIT(1) NOT NULL,
    PRIMARY KEY (offering_id),
    INDEX ix_course_migration_target (target_course_id),
    INDEX ix_course_migration_canonical (canonical_offering_id),
    INDEX ix_course_migration_term_canonical (canonical_term_offering_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course_dedup_migration_map (
    offering_id,
    original_course_id,
    target_course_id,
    canonical_offering_id,
    canonical_term_offering_id,
    has_course_code
)
SELECT
    candidate.offering_id,
    candidate.original_course_id,
    CASE
        WHEN candidate.has_course_code = b'1' THEN canonical.original_course_id
        ELSE UNHEX(SUBSTRING(SHA2(CONCAT('NO_CODE|', HEX(candidate.offering_id)), 256), 1, 32))
    END AS target_course_id,
    canonical.offering_id AS canonical_offering_id,
    term_canonical.offering_id AS canonical_term_offering_id,
    candidate.has_course_code
FROM course_dedup_migration_ranked candidate
JOIN course_dedup_migration_ranked canonical
  ON canonical.identity_key = candidate.identity_key
 AND canonical.row_rank = 1
JOIN course_dedup_migration_ranked term_canonical
  ON term_canonical.semester_id = candidate.semester_id
 AND term_canonical.identity_key = candidate.identity_key
 AND term_canonical.term_row_rank = 1;

-- 같은 시간표에서 합쳐질 Offering을 둘 이상 선택한 경우 한 행만 보존하기 위한 계획입니다.
-- 이미 학기별 canonical Offering을 참조하는 행을 우선하고, 그다음 생성 시각과 PK가 빠른 행을 남깁니다.
CREATE TABLE course_dedup_migration_timetable (
    timetable_course_id BIGINT NOT NULL,
    timetable_id BIGINT NOT NULL,
    original_offering_id BINARY(16) NOT NULL,
    canonical_term_offering_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    row_rank BIGINT NOT NULL,
    PRIMARY KEY (timetable_course_id),
    INDEX ix_course_migration_timetable_target (canonical_term_offering_id, row_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO course_dedup_migration_timetable (
    timetable_course_id,
    timetable_id,
    original_offering_id,
    canonical_term_offering_id,
    created_at,
    row_rank
)
SELECT
    tc.id,
    tc.timetable_id,
    tc.course_offering_id,
    m.canonical_term_offering_id,
    tc.created_at,
    ROW_NUMBER() OVER (
        PARTITION BY tc.timetable_id, m.canonical_term_offering_id
        ORDER BY
            (tc.course_offering_id = m.canonical_term_offering_id) DESC,
            tc.created_at,
            tc.id
    ) AS row_rank
FROM timetable_courses tc
JOIN course_dedup_migration_map m ON m.offering_id = tc.course_offering_id;

SET @offering_count_expected_after = (
    SELECT COUNT(DISTINCT canonical_term_offering_id)
    FROM course_dedup_migration_map
);
SET @timetable_course_count_expected_after = (
    SELECT COUNT(*)
    FROM course_dedup_migration_timetable
    WHERE row_rank = 1
);
SET @source_cell_count_expected_after = (
    SELECT COUNT(*)
    FROM course_source_cells cell
    JOIN course_dedup_migration_map m ON m.offering_id = cell.offering_id
    WHERE m.offering_id = m.canonical_term_offering_id
);

-- DDL 전에 실제 병합 영향을 출력합니다. 백업·복제 DB 검증 시 이 수치를 보관합니다.
SELECT
    @offering_count_before AS offerings_before,
    @offering_count_expected_after AS offerings_expected_after,
    @offering_count_before - @offering_count_expected_after AS offerings_to_merge,
    @timetable_course_count_before AS timetable_courses_before,
    @timetable_course_count_expected_after AS timetable_courses_expected_after,
    @timetable_course_count_before - @timetable_course_count_expected_after
        AS duplicate_timetable_selections_to_remove,
    @source_cell_count_before AS source_cells_before,
    @source_cell_count_expected_after AS source_cells_expected_after,
    @source_cell_count_before - @source_cell_count_expected_after AS source_cells_to_archive_only;

-- 결정적으로 만든 코드 없는 Course UUID가 기존 UUID와 충돌하면 변경 전에 중단합니다.
DELIMITER //
CREATE PROCEDURE assert_course_dedup_generated_ids()
BEGIN
    IF (SELECT COUNT(*) FROM course_dedup_migration_ranked) <> @offering_count_before
       OR (SELECT COUNT(*) FROM course_dedup_migration_map) <> @offering_count_before
       OR (SELECT COUNT(*) FROM course_dedup_migration_timetable)
            <> @timetable_course_count_before
       OR (
            SELECT COUNT(*)
            FROM course_source_cells cell
            JOIN course_dedup_migration_map m ON m.offering_id = cell.offering_id
       ) <> @source_cell_count_before THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Course migration plan does not cover every legacy reference';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM course_dedup_migration_map
        GROUP BY target_course_id
        HAVING COUNT(DISTINCT canonical_offering_id) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Different course-code identities reuse one legacy Course; split them before migration';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM course_dedup_migration_map m
        JOIN courses c ON c.id = m.target_course_id
        JOIN course_offerings canonical ON canonical.id = m.canonical_offering_id
        WHERE m.offering_id = m.canonical_offering_id
          AND m.has_course_code = b'1'
          AND NOT (
              NULLIF(UPPER(TRIM(c.course_code)), '')
              <=> NULLIF(UPPER(TRIM(canonical.course_code_snapshot)), '')
          )
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Legacy Course code differs from its canonical Offering snapshot';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM course_dedup_migration_map m
        JOIN course_dedup_migration_ranked ranked ON ranked.offering_id = m.offering_id
        WHERE m.offering_id = m.canonical_term_offering_id
        GROUP BY ranked.semester_id, m.target_course_id
        HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Course migration plan would duplicate one semester and Course mapping';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM course_dedup_migration_map m
        JOIN courses c ON c.id = m.target_course_id
        WHERE m.has_course_code = b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Generated no-code Course UUID collides with an existing Course';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM course_dedup_migration_map m
        JOIN courses c
          ON c.master_key = SHA2(CONCAT('NO_CODE|MIGRATION|', HEX(m.offering_id)), 256)
        WHERE m.has_course_code = b'0'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Generated no-code Course master key collides with an existing Course';
    END IF;
END//
DELIMITER ;

CALL assert_course_dedup_generated_ids();
DROP PROCEDURE assert_course_dedup_generated_ids;

-- 공통 Course가 실제 조회 데이터를 소유하도록 컬럼을 확장합니다.
ALTER TABLE courses
    ADD COLUMN academic_unit_id BINARY(16) NULL AFTER course_name,
    ADD COLUMN curriculum_type VARCHAR(30) NULL AFTER academic_unit_id,
    ADD COLUMN section_no VARCHAR(100) NULL AFTER curriculum_type,
    ADD COLUMN credit DECIMAL(8,3) NULL AFTER section_no,
    ADD COLUMN class_hours DECIMAL(8,3) NULL AFTER credit,
    ADD COLUMN instructor_name VARCHAR(255) NULL AFTER class_hours,
    ADD COLUMN target_grade INT NULL AFTER instructor_name,
    ADD COLUMN common_grade BIT(1) NULL AFTER target_grade,
    ADD COLUMN team_teaching BIT(1) NULL AFTER common_grade,
    ADD COLUMN note VARCHAR(2000) NULL AFTER team_teaching,
    ADD COLUMN eligibility_note VARCHAR(2000) NULL AFTER note,
    ADD COLUMN schedule_text VARCHAR(2000) NULL AFTER eligibility_note,
    ADD COLUMN classroom_text VARCHAR(2000) NULL AFTER schedule_text;

-- 코드가 있는 과목은 가장 먼저 수입된 Offering의 전체 데이터를 최초값으로 사용합니다.
UPDATE courses c
JOIN course_dedup_migration_map m
  ON m.target_course_id = c.id
 AND m.offering_id = m.canonical_offering_id
 AND m.has_course_code = b'1'
JOIN course_offerings o ON o.id = m.canonical_offering_id
SET c.course_code = NULLIF(UPPER(TRIM(o.course_code_snapshot)), ''),
    c.course_name = o.course_name_snapshot,
    c.academic_unit_id = o.academic_unit_id,
    c.curriculum_type = o.curriculum_type,
    c.section_no = o.section_no,
    c.credit = o.credit,
    c.class_hours = o.class_hours,
    c.instructor_name = o.instructor_name,
    c.target_grade = o.target_grade,
    c.common_grade = o.common_grade,
    c.team_teaching = o.team_teaching,
    c.note = o.note,
    c.eligibility_note = o.eligibility_note,
    c.schedule_text = o.schedule_text,
    c.classroom_text = o.classroom_text;

-- 코드가 없는 행은 이름과 무관하게 Offering마다 별도 Course를 만듭니다.
INSERT INTO courses (
    id,
    master_key,
    course_code,
    course_name,
    academic_unit_id,
    curriculum_type,
    section_no,
    credit,
    class_hours,
    instructor_name,
    target_grade,
    common_grade,
    team_teaching,
    note,
    eligibility_note,
    schedule_text,
    classroom_text
)
SELECT
    m.target_course_id,
    SHA2(CONCAT('NO_CODE|MIGRATION|', HEX(o.id)), 256),
    NULL,
    o.course_name_snapshot,
    o.academic_unit_id,
    o.curriculum_type,
    o.section_no,
    o.credit,
    o.class_hours,
    o.instructor_name,
    o.target_grade,
    o.common_grade,
    o.team_teaching,
    o.note,
    o.eligibility_note,
    o.schedule_text,
    o.classroom_text
FROM course_dedup_migration_map m
JOIN course_offerings o ON o.id = m.offering_id
WHERE m.has_course_code = b'0';

-- 개인 시간표가 loser Offering을 참조하면 학기별 canonical Offering으로 이동합니다.
-- 같은 시간표에서 최종 Offering이 겹치는 행은 유니크 충돌 전에 의미상 중복 한 건만 남깁니다.
DELETE tc
FROM timetable_courses tc
JOIN course_dedup_migration_timetable migration
  ON migration.timetable_course_id = tc.id
WHERE migration.row_rank > 1;

UPDATE timetable_courses tc
JOIN course_dedup_migration_timetable migration
  ON migration.timetable_course_id = tc.id
SET tc.course_offering_id = migration.canonical_term_offering_id
WHERE migration.row_rank = 1;

-- source cell은 현재 런타임과 같은 first-win 정책을 적용합니다.
-- loser 행 전체 원본은 course_import_histories.raw_payload_json과 실행 전 백업에 보존됩니다.
DELETE cell
FROM course_source_cells cell
JOIN course_dedup_migration_map m ON m.offering_id = cell.offering_id
WHERE m.offering_id <> m.canonical_term_offering_id;

-- Offering UUID는 유지하고 공통 Course FK만 재연결합니다.
UPDATE course_offerings o
JOIN course_dedup_migration_map m ON m.offering_id = o.id
SET o.course_id = m.target_course_id;

-- 이번 전환 전에 Offering이 참조했지만, 재연결 후 더 이상 참조되지 않는 구형 Course만 제거합니다.
DELETE c
FROM courses c
JOIN (
    SELECT DISTINCT original_course_id
    FROM course_dedup_migration_map
) migrated_legacy ON migrated_legacy.original_course_id = c.id
LEFT JOIN course_offerings o ON o.course_id = c.id
WHERE o.id IS NULL;

-- 공통 상세 데이터는 identity별 최초 Offering의 행만 남겨 Course에 연결합니다.
DELETE detail
FROM offering_general_education detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
WHERE m.offering_id <> m.canonical_offering_id;

ALTER TABLE offering_general_education
    ADD COLUMN course_id BINARY(16) NULL AFTER id;

UPDATE offering_general_education detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
SET detail.course_id = m.target_course_id;

ALTER TABLE offering_general_education
    DROP FOREIGN KEY fk_general_context_offering,
    DROP INDEX uk_general_context_offering,
    MODIFY COLUMN course_id BINARY(16) NOT NULL,
    DROP COLUMN offering_id,
    ADD CONSTRAINT uk_general_context_course UNIQUE (course_id),
    ADD CONSTRAINT fk_general_context_course
        FOREIGN KEY (course_id) REFERENCES courses (id);

DELETE detail
FROM offering_allowed_grades detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
WHERE m.offering_id <> m.canonical_offering_id;

ALTER TABLE offering_allowed_grades
    ADD COLUMN course_id BINARY(16) NULL AFTER id;

UPDATE offering_allowed_grades detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
SET detail.course_id = m.target_course_id;

ALTER TABLE offering_allowed_grades
    DROP FOREIGN KEY fk_allowed_grade_offering,
    DROP INDEX uk_offering_allowed_grade,
    MODIFY COLUMN course_id BINARY(16) NOT NULL,
    DROP COLUMN offering_id,
    ADD CONSTRAINT uk_course_allowed_grade UNIQUE (course_id, grade),
    ADD CONSTRAINT fk_allowed_grade_course
        FOREIGN KEY (course_id) REFERENCES courses (id);

DELETE detail
FROM offering_eligible_departments detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
WHERE m.offering_id <> m.canonical_offering_id;

ALTER TABLE offering_eligible_departments
    ADD COLUMN course_id BINARY(16) NULL AFTER id;

UPDATE offering_eligible_departments detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
SET detail.course_id = m.target_course_id;

ALTER TABLE offering_eligible_departments
    DROP FOREIGN KEY fk_eligible_department_offering,
    DROP INDEX uk_offering_eligible_department,
    MODIFY COLUMN course_id BINARY(16) NOT NULL,
    DROP COLUMN offering_id,
    ADD CONSTRAINT uk_course_eligible_department UNIQUE (course_id, department_name),
    ADD CONSTRAINT fk_eligible_department_course
        FOREIGN KEY (course_id) REFERENCES courses (id);

DELETE detail
FROM course_schedules detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
WHERE m.offering_id <> m.canonical_offering_id;

ALTER TABLE course_schedules
    ADD COLUMN course_id BINARY(16) NULL AFTER id;

UPDATE course_schedules detail
JOIN course_dedup_migration_map m ON m.offering_id = detail.offering_id
SET detail.course_id = m.target_course_id;

ALTER TABLE course_schedules
    DROP FOREIGN KEY fk_schedule_offering,
    DROP INDEX ix_schedule_offering_order,
    MODIFY COLUMN course_id BINARY(16) NOT NULL,
    DROP COLUMN offering_id,
    ADD INDEX ix_schedule_course_order (course_id, schedule_order),
    ADD CONSTRAINT fk_schedule_course
        FOREIGN KEY (course_id) REFERENCES courses (id);

-- 학기별 canonical Offering UUID 하나만 남깁니다. 이 전에 모든 inbound FK를 이동하거나 정리했습니다.
DELETE offering
FROM course_offerings offering
JOIN course_dedup_migration_map m ON m.offering_id = offering.id
WHERE m.offering_id <> m.canonical_term_offering_id;

-- Course 마스터의 identity, 검색 인덱스와 학과 FK를 확정합니다.
UPDATE courses
SET course_code = NULL
WHERE course_code IS NOT NULL
  AND TRIM(course_code) = '';

ALTER TABLE courses
    ADD CONSTRAINT uk_course_code UNIQUE (course_code),
    ADD INDEX ix_course_name (course_name),
    ADD INDEX ix_course_instructor (instructor_name),
    ADD INDEX ix_course_curriculum (curriculum_type),
    ADD CONSTRAINT fk_course_academic_unit
        FOREIGN KEY (academic_unit_id) REFERENCES academic_units (id);

-- Offering은 학기·수입 증거만 남기고 학기별 canonical UUID를 유지합니다.
ALTER TABLE course_offerings
    ADD COLUMN active BIT(1) NOT NULL DEFAULT b'1' AFTER source_row,
    DROP FOREIGN KEY fk_offering_academic_unit,
    DROP INDEX ix_offering_scope,
    DROP INDEX ix_offering_course_name,
    DROP INDEX ix_offering_instructor,
    DROP COLUMN academic_unit_id,
    DROP COLUMN section_no,
    DROP COLUMN course_code_snapshot,
    DROP COLUMN course_name_snapshot,
    DROP COLUMN credit,
    DROP COLUMN class_hours,
    DROP COLUMN instructor_name,
    DROP COLUMN target_grade,
    DROP COLUMN common_grade,
    DROP COLUMN team_teaching,
    DROP COLUMN note,
    DROP COLUMN eligibility_note,
    DROP COLUMN schedule_text,
    DROP COLUMN classroom_text,
    ADD CONSTRAINT uk_offering_semester_course UNIQUE (semester_id, course_id),
    ADD INDEX ix_offering_scope (semester_id, curriculum_type, active),
    ADD INDEX ix_offering_import (import_history_id);

ALTER TABLE course_offerings
    ALTER COLUMN active DROP DEFAULT;

-- 원본 셀은 canonical Offering 것만 남고, 사용자 시간표는 canonical Offering으로 재연결됐습니다.

-- 적용 결과를 확인합니다. 모든 *_ok 값은 1, orphan/duplicate 값은 0이어야 합니다.
SELECT
    @course_count_before AS courses_before,
    COUNT(*) AS courses_after
FROM courses;

SELECT
    @offering_count_before AS offerings_before,
    @offering_count_expected_after AS offerings_expected_after,
    COUNT(*) AS offerings_after,
    COUNT(*) = @offering_count_expected_after AS offering_count_ok
FROM course_offerings;

SELECT
    @timetable_course_count_before AS timetable_courses_before,
    @timetable_course_count_expected_after AS timetable_courses_expected_after,
    COUNT(*) AS timetable_courses_after,
    COUNT(*) = @timetable_course_count_expected_after AS timetable_course_count_ok
FROM timetable_courses;

SELECT
    @source_cell_count_before AS source_cells_before,
    @source_cell_count_expected_after AS source_cells_expected_after,
    COUNT(*) AS source_cells_after,
    COUNT(*) = @source_cell_count_expected_after AS source_cell_count_ok
FROM course_source_cells;

SELECT COUNT(*) AS orphan_offerings
FROM course_offerings o
LEFT JOIN courses c ON c.id = o.course_id
WHERE c.id IS NULL;

SELECT COUNT(*) AS duplicate_course_codes
FROM (
    SELECT course_code
    FROM courses
    WHERE course_code IS NOT NULL
    GROUP BY course_code
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS duplicate_semester_course_mappings
FROM (
    SELECT semester_id, course_id
    FROM course_offerings
    GROUP BY semester_id, course_id
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS reused_no_code_courses
FROM (
    SELECT o.course_id
    FROM course_offerings o
    JOIN courses c ON c.id = o.course_id
    WHERE c.course_code IS NULL
    GROUP BY o.course_id
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS inactive_legacy_offerings
FROM course_offerings
WHERE active = b'0';

SELECT COUNT(*) AS orphan_schedules
FROM course_schedules d
LEFT JOIN courses c ON c.id = d.course_id
WHERE c.id IS NULL;

SELECT COUNT(*) AS orphan_allowed_grades
FROM offering_allowed_grades d
LEFT JOIN courses c ON c.id = d.course_id
WHERE c.id IS NULL;

SELECT COUNT(*) AS orphan_eligible_departments
FROM offering_eligible_departments d
LEFT JOIN courses c ON c.id = d.course_id
WHERE c.id IS NULL;

SELECT COUNT(*) AS orphan_general_education
FROM offering_general_education d
LEFT JOIN courses c ON c.id = d.course_id
WHERE c.id IS NULL;

SELECT COUNT(*) AS orphan_timetable_courses
FROM timetable_courses tc
LEFT JOIN course_offerings o ON o.id = tc.course_offering_id
WHERE o.id IS NULL;

SELECT COUNT(*) AS orphan_source_cells
FROM course_source_cells cell
LEFT JOIN course_offerings o ON o.id = cell.offering_id
WHERE o.id IS NULL;

SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN (
      'courses',
      'course_offerings',
      'course_schedules',
      'offering_allowed_grades',
      'offering_eligible_departments',
      'offering_general_education'
  )
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

-- 검증 실패 시 작업 테이블을 남기고 중단합니다. 이 시점의 실패는 백업 복원 대상입니다.
DELIMITER //
CREATE PROCEDURE assert_course_dedup_migration_result()
BEGIN
    IF (SELECT COUNT(*) FROM course_offerings) <> @offering_count_expected_after
       OR (SELECT COUNT(*) FROM timetable_courses) <> @timetable_course_count_expected_after
       OR (SELECT COUNT(*) FROM course_source_cells) <> @source_cell_count_expected_after
       OR EXISTS (
            SELECT 1
            FROM course_offerings o
            LEFT JOIN courses c ON c.id = o.course_id
            WHERE c.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM course_offerings
            GROUP BY semester_id, course_id
            HAVING COUNT(*) > 1
       )
       OR EXISTS (
            SELECT 1
            FROM course_dedup_migration_map m
            LEFT JOIN course_offerings o ON o.id = m.offering_id
            WHERE m.offering_id = m.canonical_term_offering_id
              AND (o.id IS NULL OR o.course_id <> m.target_course_id)
       )
       OR EXISTS (
            SELECT 1
            FROM course_dedup_migration_map m
            JOIN course_offerings o ON o.id = m.offering_id
            WHERE m.offering_id <> m.canonical_term_offering_id
       )
       OR EXISTS (
            SELECT 1
            FROM course_offerings o
            LEFT JOIN course_dedup_migration_map m
              ON m.offering_id = o.id
             AND m.offering_id = m.canonical_term_offering_id
            WHERE m.offering_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM course_offerings o
            JOIN courses c ON c.id = o.course_id
            WHERE c.course_code IS NULL
            GROUP BY o.course_id
            HAVING COUNT(*) > 1
       )
       OR EXISTS (SELECT 1 FROM course_offerings WHERE active = b'0')
       OR EXISTS (
            SELECT 1
            FROM course_schedules d
            LEFT JOIN courses c ON c.id = d.course_id
            WHERE c.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM offering_allowed_grades d
            LEFT JOIN courses c ON c.id = d.course_id
            WHERE c.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM offering_eligible_departments d
            LEFT JOIN courses c ON c.id = d.course_id
            WHERE c.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM offering_general_education d
            LEFT JOIN courses c ON c.id = d.course_id
            WHERE c.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM timetable_courses tc
            LEFT JOIN course_offerings o ON o.id = tc.course_offering_id
            WHERE o.id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM course_dedup_migration_timetable migration
            LEFT JOIN timetable_courses tc ON tc.id = migration.timetable_course_id
            WHERE migration.row_rank = 1
              AND (
                  tc.id IS NULL
                  OR tc.timetable_id <> migration.timetable_id
                  OR tc.course_offering_id <> migration.canonical_term_offering_id
                  OR tc.created_at <> migration.created_at
              )
       )
       OR EXISTS (
            SELECT 1
            FROM course_dedup_migration_timetable migration
            JOIN timetable_courses tc ON tc.id = migration.timetable_course_id
            WHERE migration.row_rank > 1
       )
       OR EXISTS (
            SELECT 1
            FROM timetable_courses tc
            LEFT JOIN course_dedup_migration_timetable migration
              ON migration.timetable_course_id = tc.id
             AND migration.row_rank = 1
            WHERE migration.timetable_course_id IS NULL
       )
       OR EXISTS (
            SELECT 1
            FROM course_source_cells cell
            LEFT JOIN course_offerings o ON o.id = cell.offering_id
            WHERE o.id IS NULL
       ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Course migration verification failed; restore the database backup';
    END IF;
END//
DELIMITER ;

CALL assert_course_dedup_migration_result();
DROP PROCEDURE assert_course_dedup_migration_result;

DROP TABLE course_dedup_migration_timetable;
DROP TABLE course_dedup_migration_map;
DROP TABLE course_dedup_migration_ranked;
