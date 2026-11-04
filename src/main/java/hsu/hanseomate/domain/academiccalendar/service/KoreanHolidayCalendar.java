package hsu.hanseomate.domain.academiccalendar.service;

import de.focus_shift.jollyday.core.Holiday;
import de.focus_shift.jollyday.core.HolidayCalendar;
import de.focus_shift.jollyday.core.HolidayManager;
import de.focus_shift.jollyday.core.HolidayType;
import de.focus_shift.jollyday.core.ManagerParameters;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Offline KR calendar. Upgrade and verify lunar coverage before extending the upper year. */
@Component
public class KoreanHolidayCalendar {
    // The pinned 2.24.0 KR XML includes per-year lunar dates through 2035.
    private static final int LAST_VERIFIED_YEAR = 2035;
    private final HolidayManager manager;
    private final Set<LocalDate> extraDates;

    public KoreanHolidayCalendar(
            @Value("${academic-calendar.holidays.additional-dates:}") String additionalDates) {
        manager = HolidayManager.getInstance(ManagerParameters.create(HolidayCalendar.REPUBLIC_OF_KOREA));
        extraDates = Arrays.stream(additionalDates.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(LocalDate::parse).collect(Collectors.toUnmodifiableSet());
    }

    public Optional<Set<LocalDate>> dates(YearMonth month) {
        if (month.getYear() < 2026 || month.getYear() > LAST_VERIFIED_YEAR) return Optional.empty();
        Set<LocalDate> result = manager.getHolidays(Year.of(month.getYear()), HolidayType.PUBLIC_HOLIDAY)
                .stream().map(Holiday::getDate)
                .filter(date -> YearMonth.from(date).equals(month))
                .collect(Collectors.toCollection(HashSet::new));
        extraDates.stream().filter(date -> YearMonth.from(date).equals(month)).forEach(result::add);
        return Optional.of(Set.copyOf(result));
    }
}
