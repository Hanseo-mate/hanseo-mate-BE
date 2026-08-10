package hsu.hanseomate.domain.calendar.dto;

import hsu.hanseomate.domain.calendar.entity.CalendarEvent;
import java.time.LocalDate;

public record CalendarEventResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String title,
        String content
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return new CalendarEventResponse(
                event.getId(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTitle(),
                event.getContent()
        );
    }
}
