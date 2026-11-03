package hsu.hanseomate.domain.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hsu.hanseomate.domain.notices.dto.UnifiedNoticeListItemResponse;
import hsu.hanseomate.domain.notices.repository.UnifiedNoticeQueryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnifiedNoticeQueryMySqlTest {

    private MySQLContainer mysql;
    private JdbcTemplate jdbc;
    private UnifiedNoticeQueryRepository repository;

    @BeforeAll
    void connect() {
        String url = System.getenv("UNIFIED_NOTICE_TEST_MYSQL_URL");
        DriverManagerDataSource dataSource;
        if (url != null) {
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/unified_notice_query_test(?:\\?.*)?")) {
                throw new IllegalArgumentException("독립 loopback unified_notice_query_test DB만 허용합니다.");
            }
            dataSource = new DriverManagerDataSource(url,
                    System.getenv().getOrDefault("UNIFIED_NOTICE_TEST_MYSQL_USER", "root"),
                    System.getenv().getOrDefault("UNIFIED_NOTICE_TEST_MYSQL_PASSWORD", ""));
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "MySQL 실행 환경 필요");
            mysql = new MySQLContainer("mysql:8.0").withDatabaseName("unified_notice_query_test");
            mysql.start();
            dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }
        jdbc = new JdbcTemplate(dataSource);
        repository = new UnifiedNoticeQueryRepository(jdbc);
        jdbc.execute("""
                CREATE TABLE notices (
                    id BIGINT PRIMARY KEY, notice_type VARCHAR(50), origin_notice_id VARCHAR(32),
                    title VARCHAR(500), source_url VARCHAR(1024), author VARCHAR(100),
                    post_date DATE, is_hot BOOLEAN, view_count BIGINT
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                """);
        jdbc.execute("""
                CREATE TABLE student_council_notices (
                    id BIGINT PRIMARY KEY, title VARCHAR(500), author VARCHAR(100),
                    created_at DATETIME, view_count BIGINT
                ) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
                """);
    }

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM notices");
        jdbc.update("DELETE FROM student_council_notices");
    }

    @AfterAll
    void close() {
        if (jdbc != null) {
            jdbc.execute("DROP TABLE IF EXISTS notices");
            jdbc.execute("DROP TABLE IF EXISTS student_council_notices");
        }
        if (mysql != null) {
            mysql.stop();
        }
    }

    @Test
    void pagesAcrossSourcesWithDifferentCollationsWithoutMissingOrDuplicatingNotices() {
        for (int id = 1; id <= 23; id++) {
            jdbc.update("INSERT INTO notices VALUES (?, 'general', ?, ?, 'https://example.com', '작성자', '2026-11-03', FALSE, 7)",
                    id, Integer.toString(id), "학교 공지 " + id);
        }
        for (int id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO student_council_notices VALUES (?, ?, '학생회', '2025-01-01 09:00:00', 0)",
                    id, "학생회 공지 " + id);
        }
        jdbc.update("INSERT INTO notices VALUES (24, 'academic', '24', '학사 HOT', 'https://example.com', '작성자', '2025-01-01', TRUE, 9)");

        List<UnifiedNoticeListItemResponse> items = new ArrayList<>();
        items.addAll(repository.searchByTitleIgnoringSpaces("", PageRequest.of(0, 20)));
        items.addAll(repository.searchByTitleIgnoringSpaces("", PageRequest.of(1, 20)));
        assertThat(items).hasSize(27);
        assertThat(items.stream().map(i -> i.noticeType() + ":" + i.id())).doesNotHaveDuplicates();
        assertThat(items.subList(0, 5)).extracting(UnifiedNoticeListItemResponse::title)
                .containsExactly("학생회 공지 3", "학생회 공지 2", "학생회 공지 1", "학사 HOT", "학교 공지 23");
        assertThat(items.get(3).isHot()).isTrue();
        assertThat(items.get(3).viewCount()).isEqualTo(9);
        assertThat(repository.searchByTitleIgnoringSpaces("", PageRequest.of(2, 20))).isEmpty();
        assertThat(repository.searchByTitleIgnoringSpaces("학생회", PageRequest.of(0, 2)))
                .extracting(UnifiedNoticeListItemResponse::title).containsExactly("학생회 공지 3", "학생회 공지 2");
        assertThat(repository.searchByTitleIgnoringSpaces("학생회", PageRequest.of(1, 2)))
                .extracting(UnifiedNoticeListItemResponse::title).containsExactly("학생회 공지 1");
    }

    @Test
    void supportsLiteralWildcardsAndBitHotColumn() {
        jdbc.execute("ALTER TABLE notices MODIFY is_hot BIT(1)");
        jdbc.update("INSERT INTO notices VALUES (1, 'scholarship', '1', '100%_지원!', 'https://example.com', '작성자', '2026-11-03', TRUE, 0)");
        jdbc.update("INSERT INTO notices VALUES (2, 'general', '2', '100퍼센트지원!', 'https://example.com', '작성자', '2026-11-03', FALSE, 0)");
        jdbc.update("INSERT INTO student_council_notices VALUES (1, '100%_지원! 학생회', '학생회', '2025-01-01 09:00:00', 0)");
        assertThat(repository.searchByTitleIgnoringSpaces("100%_지원!", PageRequest.of(0, 20)))
                .extracting(UnifiedNoticeListItemResponse::title).containsExactly("100%_지원! 학생회", "100%_지원!");
    }
}
