package hsu.hanseomate.global.exception;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record ApiErrorResponse(
        int status,
        String message,
        String path,
        Instant timestamp
) {

    public static ApiErrorResponse of(HttpStatus status, String message, String path) {
        return new ApiErrorResponse(status.value(), message, path, Instant.now());
    }
}
