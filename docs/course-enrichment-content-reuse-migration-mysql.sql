-- 동일교과목 / 타학과 전공인정 내용 재사용 전환 (MySQL 8)
-- 실행 전 DB 백업 및 두 관리자 업로드 API 중지. 같은 DB 세션에서 전체 실행.
-- 기존 5개 이력/그룹/행 테이블이 있어야 한다. properties 변경 불필요.
-- CREATE TABLE은 암묵적 커밋: 전체 스크립트가 하나의 롤백 단위는 아니다.
-- 기존 행/이력을 삭제하거나 변경하지 않는다. DML 이관은 검증 실패 시 롤백.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS equivalent_course_contents (
    content_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    course_code VARCHAR(7) NOT NULL,
    course_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (content_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS equivalent_course_memberships (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    group_id BINARY(16) NOT NULL,
    content_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    course_code VARCHAR(7) NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    member_order INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equiv_membership_code UNIQUE (import_history_id, course_code),
    INDEX ix_equiv_membership_group_order (group_id, member_order),
    CONSTRAINT fk_equiv_membership_history FOREIGN KEY (import_history_id)
        REFERENCES equivalent_course_import_histories (id) ON DELETE CASCADE,
    CONSTRAINT fk_equiv_membership_group FOREIGN KEY (group_id)
        REFERENCES equivalent_course_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_equiv_membership_content FOREIGN KEY (content_key)
        REFERENCES equivalent_course_contents (content_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cross_major_rule_contents (
    rule_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    PRIMARY KEY (rule_key),
    INDEX ix_cross_major_content_name (course_name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cross_major_rule_memberships (
    id BINARY(16) NOT NULL,
    import_history_id BINARY(16) NOT NULL,
    rule_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_sheet VARCHAR(255) NOT NULL,
    source_row INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cross_major_membership_rule UNIQUE (import_history_id, rule_key),
    CONSTRAINT fk_cross_major_membership_history FOREIGN KEY (import_history_id)
        REFERENCES cross_major_recognition_import_histories (id) ON DELETE CASCADE,
    CONSTRAINT fk_cross_major_membership_content FOREIGN KEY (rule_key)
        REFERENCES cross_major_rule_contents (rule_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS migrate_course_enrichment_content_reuse;
DELIMITER $$
CREATE PROCEDURE migrate_course_enrichment_content_reuse()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    INSERT INTO equivalent_course_contents (content_key, course_code, course_name)
    SELECT DISTINCT SHA2(CONCAT(m.course_code, CHAR(31), m.course_name), 256),
           m.course_code, m.course_name
    FROM equivalent_course_members m
    WHERE NOT EXISTS (
        SELECT 1 FROM equivalent_course_contents c
        WHERE c.content_key = SHA2(CONCAT(m.course_code, CHAR(31), m.course_name), 256)
    );

    INSERT INTO equivalent_course_memberships
        (id, import_history_id, group_id, content_key, course_code, source_sheet, source_row, member_order)
    SELECT m.id, m.import_history_id, m.group_id,
           SHA2(CONCAT(m.course_code, CHAR(31), m.course_name), 256),
           m.course_code, m.source_sheet, m.source_row, m.member_order
    FROM equivalent_course_members m
    WHERE NOT EXISTS (SELECT 1 FROM equivalent_course_memberships n WHERE n.id = m.id);

    INSERT INTO cross_major_rule_contents
        (rule_key, student_college_name, student_department_name, student_major_name, offering_college_name, offering_department_name, offering_major_name, offering_department_key, offering_major_key, course_code, course_name_snapshot, course_name_key, effective_year, effective_semester)
    SELECT DISTINCT r.rule_key, r.student_college_name, r.student_department_name, r.student_major_name, r.offering_college_name, r.offering_department_name, r.offering_major_name, r.offering_department_key, r.offering_major_key, r.course_code, r.course_name_snapshot, r.course_name_key, r.effective_year, r.effective_semester
    FROM cross_major_recognition_rules r
    WHERE NOT EXISTS (SELECT 1 FROM cross_major_rule_contents c WHERE c.rule_key = r.rule_key);

    INSERT INTO cross_major_rule_memberships
        (id, import_history_id, rule_key, source_sheet, source_row)
    SELECT r.id, r.import_history_id, r.rule_key, r.source_sheet, r.source_row
    FROM cross_major_recognition_rules r
    WHERE NOT EXISTS (SELECT 1 FROM cross_major_rule_memberships n WHERE n.id = r.id);

    -- Compare every legacy record, not just the active snapshots.
    IF EXISTS (
        SELECT 1 FROM equivalent_course_members m
        LEFT JOIN equivalent_course_memberships n ON n.id = m.id
        LEFT JOIN equivalent_course_contents c ON c.content_key = n.content_key
        WHERE n.id IS NULL OR c.content_key IS NULL
           OR n.import_history_id <> m.import_history_id OR n.group_id <> m.group_id
           OR BINARY n.course_code <> BINARY m.course_code
           OR BINARY c.course_code <> BINARY m.course_code
           OR BINARY c.course_name <> BINARY m.course_name
           OR BINARY n.source_sheet <> BINARY m.source_sheet
           OR n.source_row <> m.source_row OR n.member_order <> m.member_order
           OR c.content_key <> SHA2(CONCAT(m.course_code, CHAR(31), m.course_name), 256)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Equivalent course backfill mismatch; DML rolled back';
    END IF;

    IF EXISTS (
        SELECT 1 FROM cross_major_recognition_rules r
        LEFT JOIN cross_major_rule_memberships n ON n.id = r.id
        LEFT JOIN cross_major_rule_contents c ON c.rule_key = n.rule_key
        WHERE n.id IS NULL OR c.rule_key IS NULL
           OR n.import_history_id <> r.import_history_id OR BINARY n.rule_key <> BINARY r.rule_key
           OR BINARY n.source_sheet <> BINARY r.source_sheet OR n.source_row <> r.source_row
           OR BINARY c.student_college_name <> BINARY r.student_college_name
           OR BINARY c.student_department_name <> BINARY r.student_department_name
           OR BINARY c.student_major_name <> BINARY r.student_major_name
           OR BINARY c.offering_college_name <> BINARY r.offering_college_name
           OR BINARY c.offering_department_name <> BINARY r.offering_department_name
           OR BINARY c.offering_major_name <> BINARY r.offering_major_name
           OR BINARY c.offering_department_key <> BINARY r.offering_department_key
           OR BINARY c.offering_major_key <> BINARY r.offering_major_key
           OR BINARY c.course_code <> BINARY r.course_code
           OR BINARY c.course_name_snapshot <> BINARY r.course_name_snapshot
           OR BINARY c.course_name_key <> BINARY r.course_name_key
           OR BINARY c.effective_year <> BINARY r.effective_year
           OR BINARY c.effective_semester <> BINARY r.effective_semester
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross major backfill mismatch; DML rolled back';
    END IF;

    IF EXISTS (
        SELECT 1 FROM equivalent_course_import_histories h
        WHERE h.history_status IN ('ACTIVE', 'SUPERSEDED')
          AND (h.member_count <> (
              SELECT COUNT(*) FROM equivalent_course_memberships n WHERE n.import_history_id = h.id
          ) OR h.group_count <> (
              SELECT COUNT(*) FROM equivalent_course_groups g WHERE g.import_history_id = h.id
          ))
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Equivalent snapshot counts mismatch; DML rolled back';
    END IF;

    IF EXISTS (
        SELECT 1 FROM cross_major_recognition_import_histories h
        WHERE h.status IN ('ACTIVE', 'SUPERSEDED')
          AND h.rule_count <> (
              SELECT COUNT(*) FROM cross_major_rule_memberships n WHERE n.import_history_id = h.id
          )
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cross major snapshot counts mismatch; DML rolled back';
    END IF;

    COMMIT;
END$$
DELIMITER ;

CALL migrate_course_enrichment_content_reuse();
DROP PROCEDURE migrate_course_enrichment_content_reuse;

SELECT 'equivalent' AS dataset,
       (SELECT COUNT(*) FROM equivalent_course_members) AS legacy_rows,
       (SELECT COUNT(*) FROM equivalent_course_memberships) AS memberships,
       (SELECT COUNT(*) FROM equivalent_course_contents) AS distinct_contents
UNION ALL
SELECT 'cross_major',
       (SELECT COUNT(*) FROM cross_major_recognition_rules),
       (SELECT COUNT(*) FROM cross_major_rule_memberships),
       (SELECT COUNT(*) FROM cross_major_rule_contents);
