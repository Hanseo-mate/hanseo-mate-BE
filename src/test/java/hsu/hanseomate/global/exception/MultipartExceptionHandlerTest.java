package hsu.hanseomate.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

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
        MockHttpServletRequest workbookRequest = request("/api/v1/timetables/major");
        MockHttpServletRequest attachmentRequest = request(
                "/api/admin/notices/1/attachments"
        );

        var workbookResponse = handler.handleMissingMultipartFile(
                new MissingServletRequestPartException("file"),
                workbookRequest
        );
        var attachmentResponse = handler.handleMissingMultipartFile(
                new MissingServletRequestPartException("file"),
                attachmentRequest
        );

        assertThat(workbookResponse.getBody()).isInstanceOf(CourseWorkbookErrorResponse.class);
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

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }
}
