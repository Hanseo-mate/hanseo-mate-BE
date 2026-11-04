package hsu.hanseomate.domain.academiccalendar;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import java.sql.*;
@Testcontainers(disabledWithoutDocker = true)
class AcademicCalendarMigrationMySqlTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("academic_calendar_test").withUsername("test").withPassword("test");
    @Test void migrationPreservesManualRowsAndCanBeRepeated() throws Exception {
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
            var statement=connection.createStatement()) {
            statement.execute("""
                CREATE TABLE school_calendar_events (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                start_date DATE NOT NULL,end_date DATE NOT NULL,title VARCHAR(500) NOT NULL,
                created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL)
                """);
            statement.execute("INSERT INTO school_calendar_events(start_date,end_date,title,created_at,updated_at) VALUES('2026-09-01','2026-09-01','manual',UTC_TIMESTAMP(),UTC_TIMESTAMP())");
            var sql=new FileSystemResource("docs/0005_academic_calendar.sql");
            ScriptUtils.executeSqlScript(connection,sql);
            ScriptUtils.executeSqlScript(connection,sql);
            try(var result=statement.executeQuery("SELECT source,active,event_type,title FROM school_calendar_events")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("source")).isEqualTo("MANUAL");
                assertThat(result.getBoolean("active")).isTrue();
                assertThat(result.getString("event_type")).isEqualTo("OTHER");
                assertThat(result.getString("title")).isEqualTo("manual");
            }
            statement.execute("UPDATE school_calendar_events SET source='HANSEO',academic_year=2026,semester=2,source_event_id='date-1'");
            assertThatThrownBy(()->statement.execute("INSERT INTO school_calendar_events(start_date,end_date,title,created_at,updated_at,source,academic_year,semester,source_event_id) VALUES('2026-09-01','2026-09-01','duplicate',UTC_TIMESTAMP(),UTC_TIMESTAMP(),'HANSEO',2026,2,'date-1')")).isInstanceOf(SQLException.class);
        }
    }
}
