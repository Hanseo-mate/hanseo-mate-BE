package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarService;
import hsu.hanseomate.domain.academiccalendar.service.AcademicNotificationPolicy;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:legacy-academic;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LegacyAcademicCalendarIntegrationTest.FixedClock.class)
@Transactional
class LegacyAcademicCalendarIntegrationTest {
    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock legacyAcademicClock() {
            return Clock.fixed(Instant.parse("2026-11-05T03:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired AcademicCalendarService calendar;
    @Autowired AcademicNotificationPolicy notifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void seedExistingCalendarShape() {
        legacy("2학기 중간고사", "2026-10-20", "2026-10-26");
        legacy("2학기 기말고사", "2026-12-15", "2026-12-21");
        legacy("동계방학", "2026-12-22", "2026-12-22");
        legacy("2026학년도 2학기 학기개시일", "2026-09-10", "2026-09-10");
    }

    void legacy(String title, String start, String end) {
        jdbc.update("""
                INSERT INTO school_calendar_events(start_date,end_date,title,source,event_type,active,created_at,updated_at)
                VALUES (?,?,?,'MANUAL','OTHER',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, LocalDate.parse(start), LocalDate.parse(end), title);
    }

    void official(String type, String start, String end) {
        jdbc.update("""
                INSERT INTO school_calendar_events(start_date,end_date,title,source,source_event_id,
                    academic_year,semester,event_type,active,created_at,updated_at)
                VALUES (?,?,?,'HANSEO',?,2026,2,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, LocalDate.parse(start), LocalDate.parse(end), type, type, type);
    }

    @Test void existingCalendarRowsProduceHomeDdaysWithoutChangingStoredData() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM school_calendar_events ORDER BY id");
        mvc.perform(get("/api/calendars/school")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].source").value("MANUAL"));
        mvc.perform(get("/api/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.academicStatus").value("IN_SEMESTER"))
                .andExpect(jsonPath("$.semesterDday.type").value("SEMESTER_END"))
                .andExpect(jsonPath("$.semesterDday.targetDate").value("2026-12-22"))
                .andExpect(jsonPath("$.semesterDday.daysRemaining").value(47))
                .andExpect(jsonPath("$.examDday.type").value("FINAL"))
                .andExpect(jsonPath("$.examDday.daysRemaining").value(40));
        assertThat(jdbc.queryForList("SELECT * FROM school_calendar_events ORDER BY id")).isEqualTo(before);
        assertThat(calendar.opening(2026, 2)).contains(LocalDate.of(2026, 9, 1));
    }

    @ParameterizedTest
    @CsvSource({"2026-10-20,MIDTERM,0", "2026-10-26,MIDTERM,0", "2026-10-27,FINAL,49",
            "2026-12-15,FINAL,0", "2026-12-21,FINAL,0"})
    void legacyExamDatesKeepInclusiveDdayRules(String date, String type, int remaining) {
        var dday = calendar.overview(LocalDate.parse(date)).examDday();
        assertThat(dday).isNotNull();
        assertThat(dday.type()).isEqualTo(type);
        assertThat(dday.daysRemaining()).isEqualTo(remaining);
    }

    @Test void vacationBoundaryAndNotificationGatesUseSameLegacyTerm() {
        var end = calendar.overview(LocalDate.of(2026, 12, 22));
        assertThat(end.semesterDday().type()).isEqualTo("SEMESTER_END");
        assertThat(end.semesterDday().daysRemaining()).isZero();
        assertThat(end.examDday()).isNull();
        assertThat(calendar.overview(LocalDate.of(2026, 12, 23)).semesterDday().targetDate())
                .isEqualTo(LocalDate.of(2027, 3, 2));
        for (String day : new String[]{"2026-12-21", "2026-12-22"}) {
            boolean allowed = day.equals("2026-12-21");
            assertThat(notifications.allows(Map.of("type", "shuttle_reservation",
                    "startsAt", day + "T18:00:00+09:00"))).isEqualTo(allowed);
            assertThat(notifications.allows(Map.of("type", "schedule", "subType", "class_start_reminder",
                    "startsAt", day + "T10:00:00+09:00"))).isEqualTo(allowed);
        }
    }

    @Test void officialSnapshotOverridesLegacyDatesWithoutMixingSources() {
        official("MIDTERM", "2026-10-21", "2026-10-27");
        official("FINAL", "2026-12-16", "2026-12-22");
        official("VACATION_START", "2026-12-23", "2026-12-23");
        var result = calendar.overview(LocalDate.of(2026, 11, 5));
        assertThat(result.semesterDday().targetDate()).isEqualTo(LocalDate.of(2026, 12, 23));
        assertThat(result.examDday().targetDate()).isEqualTo(LocalDate.of(2026, 12, 16));
    }

    @Test void incompleteOfficialSnapshotDoesNotBorrowLegacyMilestones() {
        official("OTHER", "2026-10-01", "2026-10-01");
        var result = calendar.overview(LocalDate.of(2026, 11, 5));
        assertThat(result.academicStatus()).isEqualTo("UNKNOWN");
        assertThat(result.semesterDday()).isNull();
        assertThat(result.examDday()).isNull();
    }

    @Test void conflictingLegacyMilestonesAreNotChosenArbitrarily() {
        legacy("2026학년도 2학기 기말고사", "2026-12-14", "2026-12-20");
        assertThat(calendar.overview(LocalDate.of(2026, 11, 5)).examDday()).isNull();
        legacy("동계방학 시작일", "2026-12-23", "2026-12-23");
        assertThat(calendar.overview(LocalDate.of(2026, 11, 5)).semesterDday()).isNull();
        assertThat(calendar.isTeachingDate(LocalDate.of(2026, 11, 5))).isFalse();
    }

    @Test void inactiveAndUnrelatedOrMismatchedLegacyTitlesDoNotAffectDdays() {
        legacy("2학기 기말고사", "2026-12-16", "2026-12-20");
        jdbc.update("UPDATE school_calendar_events SET active=FALSE WHERE start_date='2026-12-16'");
        legacy("1학기 기말고사", "2026-12-16", "2026-12-20");
        legacy("2027학년도 2학기 기말고사", "2026-12-16", "2026-12-20");
        legacy("동계 계절학기 기말고사", "2026-12-16", "2026-12-20");
        legacy("2학기 중간 강의평가", "2026-10-01", "2026-10-05");
        legacy("동계방학 특강", "2026-11-01", "2026-11-05");
        legacy("하계방학", "2026-11-02", "2026-11-02");
        var result = calendar.overview(LocalDate.of(2026, 11, 5));
        assertThat(result.semesterDday().targetDate()).isEqualTo(LocalDate.of(2026, 12, 22));
        assertThat(result.examDday().targetDate()).isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test void missingLegacyMidtermDoesNotSkipDirectlyToFinals() {
        jdbc.update("DELETE FROM school_calendar_events WHERE title='2학기 중간고사'");
        assertThat(calendar.overview(LocalDate.of(2026, 11, 5)).examDday()).isNull();
    }

    @Test void matchingAcademicYearSemesterAndUnicodeSpacesAreSupported() {
        jdbc.update("UPDATE school_calendar_events SET title=? WHERE title='2학기 중간고사'",
                "2026학년도\u00a02학기 중간고사");
        jdbc.update("UPDATE school_calendar_events SET title=? WHERE title='2학기 기말고사'",
                "2026학년도 2학기 기말고사");
        assertThat(calendar.overview(LocalDate.of(2026, 11, 5)).examDday().targetDate())
                .isEqualTo(LocalDate.of(2026, 12, 15));
    }

    @Test void firstSemesterStillUsesCalculatedOpeningAndItsOwnVacation() {
        legacy("1학기 중간고사", "2026-04-20", "2026-04-24");
        legacy("1학기 기말고사", "2026-06-15", "2026-06-19");
        legacy("하계방학", "2026-06-22", "2026-06-22");
        legacy("2026학년도 1학기 학기개시일", "2026-03-02", "2026-03-02");
        var opening = calendar.overview(LocalDate.of(2026, 3, 3));
        assertThat(opening.semesterDday().type()).isEqualTo("SEMESTER_START");
        assertThat(opening.semesterDday().daysRemaining()).isZero();
        assertThat(opening.examDday().type()).isEqualTo("MIDTERM");
        assertThat(calendar.overview(LocalDate.of(2026, 3, 4)).semesterDday().targetDate())
                .isEqualTo(LocalDate.of(2026, 6, 22));
    }
}
