package hsu.hanseomate.domain.academiccalendar.service;

import hsu.hanseomate.domain.academiccalendar.entity.HolidayMonth;
import hsu.hanseomate.domain.academiccalendar.repository.HolidayMonthRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Records the same offline calculation used by D-day and notification decisions. */
@Service
@RequiredArgsConstructor
public class HolidaySyncService {
    private final KoreanHolidayCalendar calendar;
    private final HolidayMonthRepository repository;
    private final Clock clock;

    public void sync(YearMonth month) {
        var dates = calendar.dates(month)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported KR holiday year: " + month.getYear()));
        repository.save(HolidayMonth.snapshot(month, dates, LocalDateTime.now(clock.withZone(ZoneOffset.UTC))));
    }
}
