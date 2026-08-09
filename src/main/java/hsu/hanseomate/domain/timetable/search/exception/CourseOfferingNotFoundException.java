package hsu.hanseomate.domain.timetable.search.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;
import java.util.UUID;

public class CourseOfferingNotFoundException extends ResourceNotFoundException {

    public CourseOfferingNotFoundException(UUID offeringId) {
        super("강좌를 찾을 수 없습니다. offeringId=" + offeringId);
    }
}
