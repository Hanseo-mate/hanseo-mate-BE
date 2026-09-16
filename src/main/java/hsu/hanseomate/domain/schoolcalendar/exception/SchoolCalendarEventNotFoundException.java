package hsu.hanseomate.domain.schoolcalendar.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class SchoolCalendarEventNotFoundException extends ResourceNotFoundException {

    public SchoolCalendarEventNotFoundException(Long calendarId) {
        super("학교 일정을 찾을 수 없습니다. calendarId=" + calendarId);
    }
}
