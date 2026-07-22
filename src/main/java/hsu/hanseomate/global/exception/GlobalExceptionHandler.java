package hsu.hanseomate.global.exception;

import hsu.hanseomate.domain.essentiallink.exception.EssentialLinkNotFoundException;
import hsu.hanseomate.domain.essentiallink.exception.InvalidCategoryException;
import hsu.hanseomate.domain.courseimport.exception.CourseImportContractException;
import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> TOO_LARGE_WORKBOOK_CODES = Set.of(
            "FILE_TOO_LARGE",
            "WORKBOOK_TOO_LARGE",
            "WORKBOOK_ARCHIVE_TOO_LARGE"
    );
    private static final Set<String> UNPROCESSABLE_WORKBOOK_CODES = Set.of(
            "NO_LECTURES_PARSED",
            "TOO_MANY_SHEETS",
            "SEMESTER_CONFLICT",
            "SEMESTER_NOT_FOUND",
            "MIXED_CURRICULUM_WORKBOOK",
            "CURRICULUM_TYPE_NOT_DETECTED",
            "CURRICULUM_TYPE_MISMATCH"
    );

    @ExceptionHandler(EssentialLinkNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            EssentialLinkNotFoundException exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCategoryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCategory(
            InvalidCategoryException exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(CourseImportContractException.class)
    public ResponseEntity<ApiErrorResponse> handleCourseImportContract(
            CourseImportContractException exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), request);
    }

    @ExceptionHandler(CourseWorkbookParseException.class)
    public ResponseEntity<CourseWorkbookErrorResponse> handleCourseWorkbookParse(
            CourseWorkbookParseException exception,
            HttpServletRequest request
    ) {
        HttpStatus status;
        if (TOO_LARGE_WORKBOOK_CODES.contains(exception.code())) {
            status = HttpStatus.CONTENT_TOO_LARGE;
        } else if (UNPROCESSABLE_WORKBOOK_CODES.contains(exception.code())) {
            status = HttpStatus.UNPROCESSABLE_CONTENT;
        } else {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(CourseWorkbookErrorResponse.of(
                status,
                exception.code(),
                exception.getMessage(),
                exception.details(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CourseWorkbookErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONTENT_TOO_LARGE;
        return ResponseEntity.status(status).body(CourseWorkbookErrorResponse.of(
                status,
                "FILE_TOO_LARGE",
                "업로드 파일이 허용 크기를 초과했습니다.",
                java.util.Map.of(),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CourseWorkbookErrorResponse> handleMissingMultipartFile(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(CourseWorkbookErrorResponse.of(
                status,
                "FILE_MISSING",
                "업로드할 엑셀 파일이 없습니다.",
                java.util.Map.of("partName", exception.getRequestPartName()),
                request.getRequestURI()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("요청값이 올바르지 않습니다.");
        return errorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("요청값이 올바르지 않습니다.");
        return errorResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return errorResponse(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected server error", exception);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, message, request.getRequestURI()));
    }
}
