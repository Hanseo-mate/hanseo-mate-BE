package hsu.hanseomate.domain.personalcalendar.dto;

import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import java.time.LocalDate;

public record PersonalCalendarEventResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String title
) {

    public static PersonalCalendarEventResponse from(PersonalCalendarEvent event) {
        return new PersonalCalendarEventResponse(
                event.getId(),
                event.getStartDate(),
                event.getEndDate(),
                event.getTitle()
        );
    }
}
