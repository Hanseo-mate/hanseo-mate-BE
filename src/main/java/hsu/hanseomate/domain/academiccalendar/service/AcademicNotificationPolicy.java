package hsu.hanseomate.domain.academiccalendar.service;

import java.time.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AcademicNotificationPolicy {
    private final AcademicCalendarService calendar;
    public boolean allows(Map<String, Object> data) {
        if (data == null) return true;
        boolean shuttle = "shuttle_reservation".equals(data.get("type"));
        boolean lesson = "schedule".equals(data.get("type"))
                && "class_start_reminder".equals(data.get("subType"));
        if (!shuttle && !lesson) return true;
        try {
            LocalDate date = OffsetDateTime.parse(String.valueOf(data.get("startsAt")))
                    .atZoneSameInstant(AcademicCalendarService.ZONE).toLocalDate();
            return calendar.isTeachingDate(date);
        } catch (java.time.format.DateTimeParseException e) {
            return false;
        }
    }
}
