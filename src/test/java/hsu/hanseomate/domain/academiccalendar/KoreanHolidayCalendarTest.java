package hsu.hanseomate.domain.academiccalendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import hsu.hanseomate.domain.academiccalendar.service.AcademicCalendarService;
import hsu.hanseomate.domain.academiccalendar.service.KoreanHolidayCalendar;
import hsu.hanseomate.domain.schoolcalendar.repository.SchoolCalendarEventRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KoreanHolidayCalendarTest {
    private final KoreanHolidayCalendar holidays = new KoreanHolidayCalendar("");

    @ParameterizedTest
    @CsvSource({
            "2026,1,2026-03-03", "2027,1,2027-03-02",
            "2030,1,2030-03-04", "2031,1,2031-03-04",
            "2035,1,2035-03-02", "2026,2,2026-09-01",
            "2029,2,2029-09-03", "2030,2,2030-09-02"
    })
    void openingSkipsWeekendsAndSubstituteHolidays(int year, int semester, String expected) {
        var service = new AcademicCalendarService(holidays, mock(SchoolCalendarEventRepository.class));
        assertThat(service.opening(year, semester)).contains(LocalDate.parse(expected));
    }

    @Test
    void lunarHolidaysAndTheirSubstitutesAreGregorianDates() {
        assertThat(holidays.dates(YearMonth.of(2026, 2)).orElseThrow()).contains(
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18));
        assertThat(holidays.dates(YearMonth.of(2026, 9)).orElseThrow()).contains(
                LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26));
        assertThat(holidays.dates(YearMonth.of(2027, 2)).orElseThrow()).contains(
                LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 7),
                LocalDate.of(2027, 2, 8), LocalDate.of(2027, 2, 9));
        assertThat(holidays.dates(YearMonth.of(2026, 5)).orElseThrow()).contains(
                LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 25));
    }

    @Test
    void configuredDatesAreCombinedWithBundledHolidaysAndWeekends() {
        // Hypothetical temporary holidays, not actual government designations.
        var extra = new KoreanHolidayCalendar(" 2026-03-03,2026-03-03,2030-03-04,2030-09-02, ");
        var service = new AcademicCalendarService(extra, mock(SchoolCalendarEventRepository.class));
        assertThat(service.opening(2026, 1)).contains(LocalDate.of(2026, 3, 4));
        assertThat(service.opening(2030, 1)).contains(LocalDate.of(2030, 3, 5));
        assertThat(service.opening(2030, 2)).contains(LocalDate.of(2030, 9, 3));
        assertThat(extra.dates(YearMonth.of(2026, 3)).orElseThrow())
                .doesNotContain(LocalDate.of(2030, 3, 4));
    }

    @Test
    void invalidConfigurationFailsInsteadOfSilentlyIgnoringAHoliday() {
        assertThatThrownBy(() -> new KoreanHolidayCalendar("2026-02-30"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void unsupportedYearIsUnknownEvenIfAdditionalDatesExist() {
        assertThat(new KoreanHolidayCalendar("2036-03-01").dates(YearMonth.of(2036, 3))).isEmpty();
        assertThat(holidays.dates(YearMonth.of(2025, 9))).isEmpty();
    }
}
