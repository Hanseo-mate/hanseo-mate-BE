package hsu.hanseomate.domain.courseenrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.domain.courseenrichment.equivalence.support.EquivalentCourseHashing;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class CourseEnrichmentMigrationMySqlContainerTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("enrichment_migration")
            .withUsername("test")
            .withPassword("test");

    @BeforeEach
    void prepareLegacySnapshots() throws Exception {
        try (Connection connection = connection()) {
            execute(connection, "SET FOREIGN_KEY_CHECKS=0");
            for (String table : List.of("equivalent_course_memberships", "equivalent_course_contents",
                    "cross_major_rule_memberships", "cross_major_rule_contents",
                    "equivalent_course_members", "equivalent_course_groups",
                    "equivalent_course_import_histories", "cross_major_recognition_rules",
                    "cross_major_recognition_import_histories")) {
                execute(connection, "DROP TABLE IF EXISTS " + table);
            }
            execute(connection, "SET FOREIGN_KEY_CHECKS=1");
            String schema = Files.readString(Path.of("docs/database-schema-mysql.sql"));
            executeScript(connection, schema.substring(
                    schema.indexOf("CREATE TABLE equivalent_course_import_histories"),
                    schema.indexOf("CREATE TABLE equivalent_course_contents")));
            seedEquivalent(connection, 2025, "이전 과목명", "SUPERSEDED");
            seedEquivalent(connection, 2026, "변경 과목명", "ACTIVE");
            seedCrossMajor(connection, 2025, "SUPERSEDED");
            seedCrossMajor(connection, 2026, "ACTIVE");
        }
    }

    @Test
    void backfillsAllHistoricalSnapshotsReusesContentAndCanRunTwice() throws Exception {
        try (Connection connection = connection()) {
            migrate(connection);
            migrate(connection);
            assertThat(count(connection, "equivalent_course_members")).isEqualTo(4);
            assertThat(count(connection, "equivalent_course_memberships")).isEqualTo(4);
            assertThat(count(connection, "equivalent_course_contents")).isEqualTo(3);
            assertThat(count(connection, "cross_major_recognition_rules")).isEqualTo(2);
            assertThat(count(connection, "cross_major_rule_memberships")).isEqualTo(2);
            assertThat(count(connection, "cross_major_rule_contents")).isEqualTo(1);
            try (var statement = connection.prepareStatement(
                    "SELECT course_name FROM equivalent_course_contents WHERE content_key=?")) {
                statement.setString(1, EquivalentCourseHashing.contentKey("0000001", "공통 과목"));
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString(1)).isEqualTo("공통 과목");
                }
            }
            try (var result = connection.createStatement().executeQuery("""
                    SELECT h.academic_year, c.course_name, n.member_order
                    FROM equivalent_course_memberships n
                    JOIN equivalent_course_contents c ON c.content_key=n.content_key
                    JOIN equivalent_course_import_histories h ON h.id=n.import_history_id
                    WHERE n.course_code='0000002' ORDER BY h.academic_year
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2025);
                assertThat(result.getString(2)).isEqualTo("이전 과목명");
                assertThat(result.getInt(3)).isEqualTo(2);
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(2026);
                assertThat(result.getString(2)).isEqualTo("변경 과목명");
            }
        }
    }

    @Test
    void validationFailureRollsBackBackfillWithoutOverwritingExistingContent() throws Exception {
        try (Connection connection = connection()) {
            String script = migration();
            executeScript(connection, script.substring(0, script.indexOf("DROP PROCEDURE")));
            insert(connection, "equivalent_course_contents",
                    "content_key", EquivalentCourseHashing.contentKey("0000001", "공통 과목"),
                    "course_code", "0000001", "course_name", "불일치 데이터");

            assertThatThrownBy(() -> migrate(connection)).isInstanceOf(SQLException.class);
            assertThat(count(connection, "equivalent_course_contents")).isEqualTo(1);
            assertThat(count(connection, "equivalent_course_memberships")).isZero();
            assertThat(count(connection, "cross_major_rule_contents")).isZero();
            assertThat(count(connection, "cross_major_rule_memberships")).isZero();
            assertThat(count(connection, "equivalent_course_members")).isEqualTo(4);
        }
    }

    private void seedEquivalent(Connection connection, int year, String secondName, String status)
            throws SQLException {
        byte[] history = uuid();
        byte[] group = uuid();
        Timestamp now = Timestamp.from(Instant.now());
        insert(connection, "equivalent_course_import_histories",
                "id", history, "import_id", "eq-" + year,
                "active_scope_key", status.equals("ACTIVE") ? year + ":2" : null,
                "canonical_hash", "a".repeat(64), "raw_file_sha256", "b".repeat(64),
                "file_name", year + ".xlsx", "schema_version", "1.0", "parser_version", "test",
                "academic_year", year, "semester", 2, "history_status", status,
                "group_count", 1, "member_count", 2, "raw_payload_json", "{}", "raw_issues_json", "[]",
                "created_at", now, "updated_at", now);
        insert(connection, "equivalent_course_groups",
                "id", group, "import_history_id", history, "source_serial", 1, "group_order", 1,
                "source_sheet", "sheet", "source_start_row", 2, "source_end_row", 3);
        for (int index = 1; index <= 2; index++) {
            insert(connection, "equivalent_course_members",
                    "id", uuid(), "import_history_id", history, "group_id", group,
                    "course_code", "000000" + index,
                    "course_name", index == 1 ? "공통 과목" : secondName,
                    "source_sheet", "sheet", "source_row", index + 1, "member_order", index);
        }
    }

    private void seedCrossMajor(Connection connection, int year, String status) throws SQLException {
        byte[] history = uuid();
        Timestamp now = Timestamp.from(Instant.now());
        insert(connection, "cross_major_recognition_import_histories",
                "id", history, "policy_year", year, "uploaded_semester", 1, "status", status,
                "active_scope_key", status.equals("ACTIVE") ? "CROSS_MAJOR:" + year : null,
                "file_name", year + ".xlsx", "raw_file_sha256", "a".repeat(64),
                "canonical_data_sha256", "b".repeat(64), "source_sheet", "rules",
                "raw_row_count", 1, "rule_count", 1, "warning_count", 0,
                "issues_json", "[]", "raw_payload_json", "[]", "created_at", now, "updated_at", now);
        insert(connection, "cross_major_recognition_rules",
                "id", uuid(), "import_history_id", history, "rule_key", "c".repeat(64),
                "student_college_name", "학생대학", "student_department_name", "학생학과",
                "student_major_name", "학생전공", "offering_college_name", "개설대학",
                "offering_department_name", "개설학과", "offering_major_name", "개설전공",
                "offering_department_key", "개설학과", "offering_major_key", "개설전공",
                "course_code", "0000001", "course_name_snapshot", "공통 과목",
                "course_name_key", "공통과목", "effective_year", 2020, "effective_semester", 1,
                "source_sheet", "rules", "source_row", 2);
    }

    private void insert(Connection connection, String table, Object... pairs) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        for (int index = 0; index < pairs.length; index += 2) {
            columns.add((String) pairs[index]);
            placeholders.add("?");
        }
        try (var statement = connection.prepareStatement("INSERT INTO " + table
                + " (" + String.join(",", columns) + ") VALUES ("
                + String.join(",", placeholders) + ")")) {
            for (int index = 0; index < pairs.length; index += 2) {
                statement.setObject(index / 2 + 1, pairs[index + 1]);
            }
            statement.executeUpdate();
        }
    }

    private byte[] uuid() {
        UUID id = UUID.randomUUID();
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits()).array();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String migration() throws Exception {
        return Files.readString(Path.of("docs/course-enrichment-content-reuse-migration-mysql.sql"));
    }

    private void migrate(Connection connection) throws Exception {
        executeScript(connection, migration());
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void executeScript(Connection connection, String script) throws SQLException {
        String delimiter = ";";
        StringBuilder pending = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.startsWith("DELIMITER ")) {
                delimiter = trimmed.substring("DELIMITER ".length());
                continue;
            }
            pending.append(line).append('\n');
            if (trimmed.endsWith(delimiter)) {
                String sql = pending.toString().trim();
                execute(connection, sql.substring(0, sql.length() - delimiter.length()));
                pending.setLength(0);
            }
        }
        assertThat(pending.toString().trim()).isEmpty();
    }
}
