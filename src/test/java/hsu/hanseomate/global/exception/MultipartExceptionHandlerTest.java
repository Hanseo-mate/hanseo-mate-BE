package hsu.hanseomate.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.parser.common.CourseWorkbookParseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class MultipartExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsWorkbookErrorOnlyForWorkbookUploadPaths() {
        var workbookRequests = java.util.List.of(
                request("/api/v1/timetables/major"),
                request("/api/v1/timetables/general-education"),
                request("/api/admin/course-enrichments/equivalent-courses/imports"),
                request("/api/admin/course-enrichments/cross-major-recognitions/imports")
        );
        MockHttpServletRequest attachmentRequest = request(
                "/api/admin/notices/1/attachments"
        );

        var workbookResponses = workbookRequests.stream()
                .map(workbookRequest -> handler.handleMissingMultipartFile(
                        new MissingServletRequestPartException("file"),
                        workbookRequest
                ))
                .toList();
        var attachmentResponse = handler.handleMissingMultipartFile(
                new MissingServletRequestPartException("file"),
                attachmentRequest
        );

        assertThat(workbookResponses)
                .allSatisfy(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getHeaders().getContentType())
                            .isEqualTo(MediaType.APPLICATION_JSON);
                    assertThat(response.getBody())
                            .isInstanceOfSatisfying(
                                    CourseWorkbookErrorResponse.class,
                                    body -> {
                                        assertThat(body.code()).isEqualTo("FILE_MISSING");
                                        assertThat(body.details()).containsEntry("partName", "file");
                                    }
                            );
                });
        assertThat(attachmentResponse.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(attachmentResponse.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void returnsJsonApiErrorsForNonWorkbookMultipartFailures() {
        MockHttpServletRequest request = request("/api/admin/notices/1/images");

        var tooLarge = handler.handleMaxUploadSize(
                new MaxUploadSizeExceededException(1L),
                request
        );
        var malformed = handler.handleMalformedMultipart(
                new MultipartException("malformed"),
                request
        );

        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(tooLarge.getBody()).isInstanceOf(ApiErrorResponse.class);
        assertThat(tooLarge.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void returnsWorkbookTooLargeErrorForCourseEnrichmentImports() {
        var paths = java.util.List.of(
                "/api/admin/course-enrichments/equivalent-courses/imports",
                "/api/admin/course-enrichments/cross-major-recognitions/imports"
        );

        paths.forEach(path -> {
            var response = handler.handleMaxUploadSize(
                    new MaxUploadSizeExceededException(1L),
                    request(path)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody())
                    .isInstanceOfSatisfying(
                            CourseWorkbookErrorResponse.class,
                            body -> assertThat(body.code()).isEqualTo("FILE_TOO_LARGE")
                    );
        });
    }

    @Test
    void returnsWorkbookErrorForMalformedCourseEnrichmentMultipart() {
        var paths = java.util.List.of(
                "/api/admin/course-enrichments/equivalent-courses/imports",
                "/api/admin/course-enrichments/cross-major-recognitions/imports"
        );

        paths.forEach(path -> {
            var response = handler.handleMalformedMultipart(
                    new MultipartException("malformed"),
                    request(path)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody())
                    .isInstanceOfSatisfying(
                            CourseWorkbookErrorResponse.class,
                            body -> assertThat(body.code()).isEqualTo("MALFORMED_MULTIPART")
                    );
        });
    }

    @Test
    void returnsUnprocessableContentForCrossMajorSourceSheetDetectionFailures() {
        java.util.List.of("SOURCE_SHEET_NOT_FOUND", "SOURCE_SHEET_CONFLICT")
                .forEach(code -> {
                    var response = handler.handleCourseWorkbookParse(
                            new CourseWorkbookParseException(code, "원본 시트 감지 실패"),
                            request("/api/admin/course-enrichments/cross-major-recognitions/imports")
                    );

                    assertThat(response.getStatusCode())
                            .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().code()).isEqualTo(code);
                });
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
