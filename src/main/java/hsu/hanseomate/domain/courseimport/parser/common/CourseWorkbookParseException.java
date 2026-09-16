package hsu.hanseomate.domain.courseimport.parser.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raised when an uploaded workbook cannot be safely interpreted.
 *
 * <p>The error code lets the HTTP layer return stable responses without
 * depending on parser implementation details.</p>
 */
public final class CourseWorkbookParseException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    public CourseWorkbookParseException(String code, String message) {
        this(code, message, Map.of(), null);
    }

    public CourseWorkbookParseException(String code, String message, Map<String, ?> details) {
        this(code, message, details, null);
    }

    public CourseWorkbookParseException(
            String code,
            String message,
            Map<String, ?> details,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
