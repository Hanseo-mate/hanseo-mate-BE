package hsu.hanseomate.domain.calendar.service;

import hsu.hanseomate.domain.calendar.dto.CalendarEventResponse;
import hsu.hanseomate.domain.calendar.dto.UnifiedCalendarEventResponse;
import hsu.hanseomate.domain.calendar.type.CalendarEventType;
import hsu.hanseomate.domain.personalcalendar.dto.PersonalCalendarEventResponse;
import hsu.hanseomate.domain.personalcalendar.service.PersonalCalendarEventService;
import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventResponse;
import hsu.hanseomate.domain.schoolcalendar.service.SchoolCalendarEventService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnifiedCalendarService {

    private static final Comparator<UnifiedCalendarEventResponse> EVENT_ORDER =
            Comparator.comparing(UnifiedCalendarEventResponse::startDate)
                    .thenComparing(UnifiedCalendarEventResponse::endDate)
                    .thenComparing(UnifiedCalendarEventResponse::calendarType)
                    .thenComparing(UnifiedCalendarEventResponse::id);

    private final SchoolCalendarEventService schoolCalendarEventService;
    private final CalendarEventService calendarEventService;
    private final PersonalCalendarEventService personalCalendarEventService;

    public List<UnifiedCalendarEventResponse> getEvents(Optional<Long> currentUserId) {
        List<UnifiedCalendarEventResponse> events = new ArrayList<>();
        schoolCalendarEventService.getEvents().stream()
                .map(this::schoolEvent)
                .forEach(events::add);
        calendarEventService.getEvents().stream()
                .map(this::studentCouncilEvent)
                .forEach(events::add);
        currentUserId.ifPresent(userId -> personalCalendarEventService.getEvents(userId)
                .stream()
                .map(this::personalEvent)
                .forEach(events::add));
        return events.stream().sorted(EVENT_ORDER).toList();
    }

    private UnifiedCalendarEventResponse schoolEvent(SchoolCalendarEventResponse event) {
        return new UnifiedCalendarEventResponse(
                event.id(),
                CalendarEventType.SCHOOL,
                event.startDate(),
                event.endDate(),
                event.title()
        );
    }

    private UnifiedCalendarEventResponse studentCouncilEvent(CalendarEventResponse event) {
        return new UnifiedCalendarEventResponse(
                event.id(),
                CalendarEventType.STUDENT_COUNCIL,
                event.startDate(),
                event.endDate(),
                event.title()
        );
    }

    private UnifiedCalendarEventResponse personalEvent(PersonalCalendarEventResponse event) {
        return new UnifiedCalendarEventResponse(
                event.id(),
                CalendarEventType.PERSONAL,
                event.startDate(),
                event.endDate(),
                event.title()
        );
    }
}
