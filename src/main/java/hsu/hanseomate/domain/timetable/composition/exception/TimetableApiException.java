package hsu.hanseomate.domain.timetable.composition.exception;

import hsu.hanseomate.domain.timetable.composition.dto.TimetableCourseResponse;
import hsu.hanseomate.domain.timetable.composition.type.TimetableErrorCode;
import java.util.List;
import lombok.Getter;

@Getter
public class TimetableApiException extends RuntimeException {

    private final TimetableErrorCode errorCode;
    private final List<TimetableCourseResponse> conflicts;

    public TimetableApiException(TimetableErrorCode errorCode) {
        this(errorCode, List.of());
    }

    public TimetableApiException(
            TimetableErrorCode errorCode,
            List<TimetableCourseResponse> conflicts
    ) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.conflicts = List.copyOf(conflicts);
    }
}
