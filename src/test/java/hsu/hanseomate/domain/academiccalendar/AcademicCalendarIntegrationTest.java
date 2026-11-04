package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static hsu.hanseomate.support.AdminJwtRequestPostProcessor.adminJwt;
import hsu.hanseomate.domain.academiccalendar.service.*;
import hsu.hanseomate.domain.academiccalendar.entity.HolidayMonth;
import hsu.hanseomate.domain.academiccalendar.repository.HolidayMonthRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:academic;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AcademicCalendarIntegrationTest {
    @Autowired AcademicCalendarService service;
    @Autowired AcademicNotificationPolicy policy;
    @Autowired HolidayMonthRepository holidays;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void seed() {
        event(2026,1,"SEMESTER_START","2026-03-02","2026-03-02","공식 학기개시일");
        event(2026,1,"MIDTERM","2026-04-20","2026-04-24","중간고사");
        event(2026,1,"FINAL","2026-06-15","2026-06-19","기말고사");
        event(2026,1,"VACATION_START","2026-06-22","2026-06-22","하계방학");
        event(2026,2,"MIDTERM","2026-10-20","2026-10-26","중간고사");
        event(2026,2,"FINAL","2026-12-15","2026-12-21","기말고사");
        event(2026,2,"VACATION_START","2026-12-22","2026-12-22","동계방학");
        event(2026,2,"OTHER","2026-12-22","2027-01-13","계절학기");
    }

    void event(int year,int term,String type,String start,String end,String title) {
        jdbc.update("""
            INSERT INTO school_calendar_events(start_date,end_date,title,source,source_event_id,
                academic_year,semester,event_type,active,created_at,updated_at)
            VALUES (?,?,?,'HANSEO',?,?,?,?,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """, LocalDate.parse(start),LocalDate.parse(end),title,UUID.randomUUID().toString(),year,term,type);
    }

    @Test void calculationOverridesOfficialOpeningAndHandlesSubstituteHolidays() {
        assertThat(service.opening(2026,1)).contains(LocalDate.parse("2026-03-03"));
        assertThat(service.overview(LocalDate.parse("2027-03-01")).semesterDday().targetDate())
                .isEqualTo(LocalDate.parse("2027-03-02"));
        var start = service.overview(LocalDate.parse("2026-03-03"));
        assertThat(start.semesterDday().type()).isEqualTo("SEMESTER_START");
        assertThat(start.semesterDday().daysRemaining()).isZero();
        assertThat(start.examDday().type()).isEqualTo("MIDTERM");
        assertThat(service.overview(LocalDate.parse("2026-03-04")).semesterDday().type()).isEqualTo("SEMESTER_END");
    }

    @ParameterizedTest
    @CsvSource({"2026-04-19,MIDTERM,1","2026-04-20,MIDTERM,0","2026-04-22,MIDTERM,0",
            "2026-04-24,MIDTERM,0","2026-04-25,FINAL,51","2026-06-14,FINAL,1",
            "2026-06-15,FINAL,0","2026-06-19,FINAL,0","2026-10-26,MIDTERM,0",
            "2026-10-27,FINAL,49","2026-12-21,FINAL,0"})
    void examBoundaries(String date,String type,long remaining) {
        var result = service.overview(LocalDate.parse(date)).examDday();
        assertThat(result.type()).isEqualTo(type);
        assertThat(result.daysRemaining()).isEqualTo(remaining);
    }

    @Test void vacationDayIsEndDdayButDisablesRemindersAndNextDayCountsNextOpening() {
        var vacation = service.overview(LocalDate.parse("2026-06-22"));
        assertThat(vacation.academicStatus()).isEqualTo("VACATION");
        assertThat(vacation.semesterDday().type()).isEqualTo("SEMESTER_END");
        assertThat(vacation.semesterDday().daysRemaining()).isZero();
        assertThat(vacation.examDday()).isNull();
        assertThat(service.isTeachingDate(LocalDate.parse("2026-06-21"))).isTrue();
        assertThat(service.isTeachingDate(LocalDate.parse("2026-06-22"))).isFalse();
        assertThat(service.overview(LocalDate.parse("2026-06-23")).semesterDday().targetDate())
                .isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(service.overview(LocalDate.parse("2026-06-20")).examDday()).isNull();
        assertThat(service.overview(LocalDate.parse("2026-12-23")).semesterDday().targetDate())
                .isEqualTo(LocalDate.parse("2027-03-02"));
        assertThat(service.isTeachingDate(LocalDate.parse("2027-01-04"))).isFalse();
    }

    @Test void missingOrAmbiguousDataDoesNotInventDates() {
        assertThat(service.overview(LocalDate.parse("2036-09-01")).academicStatus()).isEqualTo("UNKNOWN");
        assertThat(service.isTeachingDate(LocalDate.parse("2036-09-01"))).isFalse();
        jdbc.update("DELETE FROM school_calendar_events WHERE academic_year=2026 AND semester=1 AND event_type='VACATION_START'");
        assertThat(service.overview(LocalDate.parse("2026-04-01")).semesterDday()).isNull();
        assertThat(service.isTeachingDate(LocalDate.parse("2026-04-01"))).isFalse();
    }

    @Test void missingOrStaleSnapshotsCannotChangeOpeningOrReminders() {
        holidays.deleteAll();
        assertThat(service.opening(2026, 1)).contains(LocalDate.of(2026, 3, 3));
        assertThat(service.isTeachingDate(LocalDate.of(2026, 9, 1))).isTrue();
        holidays.saveAndFlush(HolidayMonth.snapshot(YearMonth.of(2026, 9),
                Set.of(LocalDate.of(2026, 9, 1)), LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertThat(service.opening(2026, 2)).contains(LocalDate.of(2026, 9, 1));
        assertThat(service.isTeachingDate(LocalDate.of(2026, 9, 1))).isTrue();
    }

    @Test void publicSchoolAndUnifiedApisIncludeImportedEventsWithoutExposingPersonalEvents() throws Exception {
        mvc.perform(get("/api/calendars/school").param("academicYear","2026").param("semester","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].source").value("HANSEO"))
                .andExpect(jsonPath("$[0].academicYear").value(2026));
        mvc.perform(get("/api/calendars/school").with(adminJwt()).param("academicYear","2026").param("semester","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/calendars/all")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calendarType").value("SCHOOL"));
        mvc.perform(get("/api/calendars/school").param("semester","3")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/academic-calendar/sync")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.academicStatus").exists())
                .andExpect(jsonPath("$.semesterDday").hasJsonPath())
                .andExpect(jsonPath("$.examDday").hasJsonPath());
    }

    @Test void inactiveEventsAreHiddenAndManualEventsRetainTheirApi() throws Exception {
        jdbc.update("UPDATE school_calendar_events SET active=FALSE WHERE academic_year=2026 AND semester=2");
        mvc.perform(get("/api/calendars/school").param("academicYear","2026").param("semester","2"))
                .andExpect(jsonPath("$.length()").value(0));
        Long id = jdbc.queryForObject("SELECT MIN(id) FROM school_calendar_events",Long.class);
        mvc.perform(delete("/api/admin/school-calendars/"+id).with(adminJwt())).andExpect(status().isBadRequest());
    }

    @Test void sendTimePolicyOnlyFiltersRelevantNotificationsAndUsesKoreanOccurrenceDate() {
        assertThat(policy.allows(Map.of("type","notice"))).isTrue();
        assertThat(policy.allows(Map.of("type","schedule"))).isTrue();
        assertThat(policy.allows(Map.of("type","shuttle_reservation","startsAt","2026-10-14T18:00:00+09:00"))).isTrue();
        assertThat(policy.allows(Map.of("type","shuttle_reservation","startsAt","2026-12-23T18:00:00+09:00"))).isFalse();
        assertThat(policy.allows(Map.of("type","schedule","subType","class_start_reminder",
                "startsAt","2026-06-21T15:00:00Z"))).isFalse(); // June 22 KST
        assertThat(policy.allows(Map.of("type","shuttle_reservation"))).isFalse();
    }
}
