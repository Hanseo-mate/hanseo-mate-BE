package hsu.hanseomate.global.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record CourseWorkbookErrorResponse(
        int status,
        String code,
        String message,
        Map<String, Object> details,
        String path,
        Instant timestamp
) {

    public static CourseWorkbookErrorResponse of(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            String path
    ) {
        return new CourseWorkbookErrorResponse(
                status.value(), code, message, details, path, Instant.now()
        );
    }
}
