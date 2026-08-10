package hsu.hanseomate.domain.schoolcalendar.dto;

import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import java.time.LocalDate;

public record SchoolCalendarEventResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String title
) {

    public static SchoolCalendarEventResponse from(SchoolCalendarEvent event) {
        return new SchoolCalendarEventResponse(
                event.getId(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTitle()
        );
    }
}
