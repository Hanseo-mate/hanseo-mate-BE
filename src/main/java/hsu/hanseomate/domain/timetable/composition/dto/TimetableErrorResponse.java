package hsu.hanseomate.domain.timetable.composition.dto;

import hsu.hanseomate.domain.timetable.composition.exception.TimetableApiException;
import java.time.Instant;
import java.util.List;

public record TimetableErrorResponse(
        int status,
        String code,
        String message,
        String path,
        Instant timestamp,
        List<TimetableCourseResponse> conflicts
) {
    public static TimetableErrorResponse of(
            TimetableApiException exception,
            String path
    ) {
        return new TimetableErrorResponse(
                exception.getErrorCode().getStatus().value(),
                exception.getErrorCode().name(),
                exception.getMessage(),
                path,
                Instant.now(),
                exception.getConflicts()
        );
    }
}
