package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.academiccalendar.repository.HolidayMonthRepository;
import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarService;
import hsu.hanseomate.domain.academiccalendar.service.AcademicNotificationPolicy;
import hsu.hanseomate.domain.academiccalendar.service.HolidaySyncService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:academic-extra;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        // Hypothetical temporary holiday used only by this test.
        "academic-calendar.holidays.additional-dates=2026-09-01"
})
@ActiveProfiles("test")
@Transactional
class AcademicAdditionalHolidayIntegrationTest {
    @Autowired AcademicCalendarService calendar;
    @Autowired AcademicNotificationPolicy policy;
    @Autowired HolidaySyncService sync;
    @Autowired HolidayMonthRepository snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void springConfigurationMovesOpeningDdayAndBothReminderGatesTogether() {
        jdbc.update("""
                INSERT INTO school_calendar_events(start_date,end_date,title,source,academic_year,
                    semester,event_type,active,created_at,updated_at)
                VALUES ('2026-12-22','2026-12-22','동계방학','HANSEO',2026,
                    2,'VACATION_START',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        assertThat(calendar.opening(2026, 2)).contains(LocalDate.of(2026, 9, 2));
        assertThat(calendar.overview(LocalDate.of(2026, 9, 2)).semesterDday().daysRemaining()).isZero();
        assertThat(calendar.overview(LocalDate.of(2026, 9, 2)).semesterDday().type())
                .isEqualTo("SEMESTER_START");
        for (String date : new String[]{"2026-09-01", "2026-09-02"}) {
            boolean allowed = date.equals("2026-09-02");
            assertThat(policy.allows(Map.of("type", "shuttle_reservation",
                    "startsAt", date + "T18:00:00+09:00"))).isEqualTo(allowed);
            assertThat(policy.allows(Map.of("type", "schedule", "subType", "class_start_reminder",
                    "startsAt", date + "T10:00:00+09:00"))).isEqualTo(allowed);
        }
        sync.sync(YearMonth.of(2026, 9));
        assertThat(snapshots.findById("2026-09").orElseThrow().dates())
                .contains(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 25));
    }
}
