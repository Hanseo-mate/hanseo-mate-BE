package hsu.hanseomate.domain.calendar.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class CalendarEventNotFoundException extends ResourceNotFoundException {

    public CalendarEventNotFoundException(Long calendarId) {
        super("학생회 일정을 찾을 수 없습니다. calendarId=" + calendarId);
    }
}
