package hsu.hanseomate.domain.calendar.service;

import hsu.hanseomate.domain.calendar.dto.CalendarEventRequest;
import hsu.hanseomate.domain.calendar.dto.CalendarEventResponse;
import hsu.hanseomate.domain.calendar.entity.CalendarEvent;
import hsu.hanseomate.domain.calendar.exception.CalendarEventNotFoundException;
import hsu.hanseomate.domain.calendar.repository.CalendarEventRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private final CalendarEventRepository calendarEventRepository;

    public List<CalendarEventResponse> getEvents() {
        return calendarEventRepository.findAllByOrderByStartDateAscEndDateAscIdAsc()
                .stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public CalendarEventResponse createEvent(CalendarEventRequest request) {
        validateDateRange(request.startDate(), request.endDate());
        CalendarEvent event = CalendarEvent.create(
                request.startDate(),
                request.endDate(),
                request.title().trim(),
                request.content()
        );
        return CalendarEventResponse.from(calendarEventRepository.saveAndFlush(event));
    }

    @Transactional
    public CalendarEventResponse updateEvent(
            Long calendarId,
            CalendarEventRequest request
    ) {
        CalendarEvent event = findEvent(calendarId);
        validateDateRange(request.startDate(), request.endDate());
        event.update(
                request.startDate(),
                request.endDate(),
                request.title().trim(),
                request.content()
        );
        calendarEventRepository.flush();
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long calendarId) {
        CalendarEvent event = findEvent(calendarId);
        calendarEventRepository.delete(event);
        calendarEventRepository.flush();
    }

    private CalendarEvent findEvent(Long calendarId) {
        return calendarEventRepository.findById(calendarId)
                .orElseThrow(() -> new CalendarEventNotFoundException(calendarId));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException(
                    "일정 종료일은 시작일보다 빠를 수 없습니다."
            );
        }
    }
}
