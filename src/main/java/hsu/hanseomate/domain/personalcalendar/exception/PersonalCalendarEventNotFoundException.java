package hsu.hanseomate.domain.personalcalendar.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class PersonalCalendarEventNotFoundException extends ResourceNotFoundException {

    public PersonalCalendarEventNotFoundException(Long calendarId) {
        super("내 일정을 찾을 수 없습니다. calendarId=" + calendarId);
    }
}
