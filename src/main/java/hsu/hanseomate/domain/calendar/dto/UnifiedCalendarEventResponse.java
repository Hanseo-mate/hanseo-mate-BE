package hsu.hanseomate.domain.calendar.dto;

import hsu.hanseomate.domain.calendar.type.CalendarEventType;
import java.time.LocalDate;

public record UnifiedCalendarEventResponse(
        Long id,
        CalendarEventType calendarType,
        LocalDate startDate,
        LocalDate endDate,
        String title
) {
}
