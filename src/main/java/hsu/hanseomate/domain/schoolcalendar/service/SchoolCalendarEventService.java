package hsu.hanseomate.domain.schoolcalendar.service;

import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventRequest;
import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventResponse;
import hsu.hanseomate.domain.schoolcalendar.entity.SchoolCalendarEvent;
import hsu.hanseomate.domain.schoolcalendar.exception.SchoolCalendarEventNotFoundException;
import hsu.hanseomate.domain.schoolcalendar.repository.SchoolCalendarEventRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchoolCalendarEventService {

    private final SchoolCalendarEventRepository schoolCalendarEventRepository;

    public List<SchoolCalendarEventResponse> getEvents() {
        return schoolCalendarEventRepository.findAllByOrderByStartDateAscEndDateAscIdAsc()
                .stream()
                .map(SchoolCalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public SchoolCalendarEventResponse createEvent(SchoolCalendarEventRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        SchoolCalendarEvent event = SchoolCalendarEvent.create(
                request.startDate(),
                request.endDate(),
                request.title().trim()
        );
        return SchoolCalendarEventResponse.from(
                schoolCalendarEventRepository.saveAndFlush(event)
        );
    }

    @Transactional
    public SchoolCalendarEventResponse updateEvent(
            Long calendarId,
            SchoolCalendarEventRequest request
    ) {
        SchoolCalendarEvent event = findEventForUpdate(calendarId);
        validateDateRange(request.startDate(), request.endDate());
        event.update(
                request.startDate(),
                request.endDate(),
                request.title().trim()
        );
        schoolCalendarEventRepository.flush();
        return SchoolCalendarEventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long calendarId) {
        SchoolCalendarEvent event = findEventForUpdate(calendarId);
        schoolCalendarEventRepository.delete(event);
        schoolCalendarEventRepository.flush();
    }

    private SchoolCalendarEvent findEventForUpdate(Long calendarId) {
        return schoolCalendarEventRepository.findByIdForUpdate(calendarId)
                .orElseThrow(() -> new SchoolCalendarEventNotFoundException(calendarId));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException(
                    "일정 종료일은 시작일보다 빠를 수 없습니다."
            );
        }
    }
}
