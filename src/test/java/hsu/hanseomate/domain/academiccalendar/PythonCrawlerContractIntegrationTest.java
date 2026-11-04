package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hsu.hanseomate.domain.academiccalendar.client.AcademicCalendarCrawlerClient;
import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarService;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Actual Spring -> Python HTTP + SQLite persistence; exported rows are read by H2.
 * This test deliberately does NOT claim a shared MySQL transaction verification.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:academic;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "ACADEMIC_CALENDAR_TEST_PYTHON", matches = ".+")
class PythonCrawlerContractIntegrationTest {
    @Autowired AcademicCalendarService calendar;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @TempDir Path scratch;

    @Test void actualPythonApiPersistsFixturesAndItsRowsSupportSpringCalendarAndDdays() throws Exception {
        String python = System.getenv("ACADEMIC_CALENDAR_TEST_PYTHON");
        String source = System.getenv("ACADEMIC_CALENDAR_TEST_CRAWLER_ROOT");
        assertThat(source).as("explicit crawler checkout required; no production fallback").isNotBlank();
        Path output = scratch.resolve("python.log");
        Process process = new ProcessBuilder(python, "-B",
                "src/test/resources/academic-calendar/python_fixture_server.py", source, scratch.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            Path port = scratch.resolve("port.txt");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(port) && process.isAlive() && System.nanoTime() < deadline) Thread.sleep(50);
            assertThat(Files.exists(port)).withFailMessage("Python startup failed: %s", Files.readString(output)).isTrue();
            String url = "http://127.0.0.1:" + Files.readString(port);
            var client = new AcademicCalendarCrawlerClient(RestClient.builder(), url);
            var http = RestClient.create(url);
            assertThat(http.get().uri("/academic-calendar-crawl/status").retrieve()
                    .body(JsonNode.class).path("status").asString()).isEqualTo("idle");
            assertThatThrownBy(() -> http.get().uri("/academic-calendar-crawl/status?run_id=unknown")
                    .retrieve().body(JsonNode.class))
                    .isInstanceOf(org.springframework.web.client.HttpClientErrorException.NotFound.class);

            var receipt = client.request(2026);
            assertThat(receipt.status()).isEqualTo("starting");
            JsonNode state = completed(http, receipt.run_id());
            assertThat(state.path("status").asString()).isEqualTo("completed");
            assertThat(state.path("academic_year").asInt()).isEqualTo(2026);
            assertThat(state.path("results").path("1").path("event_count").asInt()).isEqualTo(34);
            assertThat(state.path("results").path("2").path("event_count").asInt()).isEqualTo(33);
            assertThat(Instant.parse(state.path("finished_at").asString())).isNotNull();

            JsonNode before = http.get().uri("/__test__/rows").retrieve().body(JsonNode.class);
            state = completed(http, client.request(2026).run_id());
            assertThat(state.path("results").path("1").path("unchanged").asInt()).isEqualTo(34);
            assertThat(state.path("results").path("2").path("unchanged").asInt()).isEqualTo(33);
            JsonNode after = http.get().uri("/__test__/rows").retrieve().body(JsonNode.class);
            assertThat(after.size()).isEqualTo(69); // 67 source + manual + inactive obsolete.
            for (int i = 0; i < before.size(); i++) {
                assertThat(after.get(i).path("id")).isEqualTo(before.get(i).path("id"));
                assertThat(after.get(i).path("created_at")).isEqualTo(before.get(i).path("created_at"));
            }
            assertThat(after.get(0).path("source").asString()).isEqualTo("MANUAL");
            assertThat(after.get(0).path("active").asBoolean()).isTrue();
            assertThat(after.get(1).path("active").asBoolean()).isFalse();
            // Explicit SQLite -> JSON -> H2 bridge, not a shared database test.
            for (JsonNode row : after) {
                jdbc.update("""
                        INSERT INTO school_calendar_events(start_date,end_date,title,source,source_event_id,
                            academic_year,semester,event_type,active,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                        """, LocalDate.parse(row.path("start_date").asString()),
                        LocalDate.parse(row.path("end_date").asString()), row.path("title").asString(),
                        row.path("source").asString(), row.path("source_event_id").asString(null),
                        row.path("academic_year").isNull() ? null : row.path("academic_year").asInt(),
                        row.path("semester").isNull() ? null : row.path("semester").asInt(),
                        row.path("event_type").asString(), row.path("active").asBoolean());
            }
            mvc.perform(get("/api/calendars/school").param("academicYear", "2026").param("semester", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(34));
            mvc.perform(get("/api/calendars/all")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(68));
            mvc.perform(get("/api/home")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.semesterDday").hasJsonPath())
                    .andExpect(jsonPath("$.examDday").hasJsonPath());
            assertThat(calendar.opening(2026, 1)).contains(LocalDate.of(2026, 3, 3));
            assertThat(calendar.overview(LocalDate.of(2026, 4, 24)).examDday().daysRemaining()).isZero();
            assertThat(calendar.overview(LocalDate.of(2026, 4, 25)).examDday().type()).isEqualTo("FINAL");
            assertThat(calendar.overview(LocalDate.of(2026, 6, 22)).semesterDday().daysRemaining()).isZero();
            assertThat(calendar.isTeachingDate(LocalDate.of(2026, 6, 22))).isFalse();
            assertThat(calendar.overview(LocalDate.of(2026, 12, 23)).semesterDday().targetDate())
                    .isEqualTo(LocalDate.of(2027, 3, 2));
        } finally {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
        }
    }

    private JsonNode completed(RestClient http, String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        JsonNode state;
        do {
            state = http.get().uri("/academic-calendar-crawl/status?run_id={id}", id)
                    .retrieve().body(JsonNode.class);
            if (!state.path("status").asString().matches("starting|running")) return state;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Python fixture did not finish: " + state);
    }
}
