package hsu.hanseomate.domain.courseimport.controller;

import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.TimetableParseResultRequest;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.service.CourseImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부 강좌 수입", description = "FastAPI parser가 호출하는 JSON API")
@RestController
@RequestMapping(path = "/api/internal/course-imports", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InternalCourseImportController {

    private static final String IMPORT_ID_HEADER = "X-IMPORT-ID";
    private static final String SCHEMA_VERSION_HEADER = "X-PARSER-SCHEMA-VERSION";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final CourseImportService courseImportService;

    @Operation(summary = "전공 강좌 학기 스냅샷 저장")
    @PostMapping(path = "/major", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseImportResponse importMajorCourses(
            @Valid @RequestBody TimetableParseResultRequest request,
            @RequestHeader(IMPORT_ID_HEADER) String importId,
            @RequestHeader(SCHEMA_VERSION_HEADER) String schemaVersion,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey
    ) {
        return courseImportService.importCourses(
                request,
                CurriculumType.MAJOR,
                importId,
                schemaVersion,
                idempotencyKey
        );
    }

    @Operation(summary = "교양 강좌 학기 스냅샷 저장")
    @PostMapping(path = "/general-education", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CourseImportResponse importGeneralEducationCourses(
            @Valid @RequestBody TimetableParseResultRequest request,
            @RequestHeader(IMPORT_ID_HEADER) String importId,
            @RequestHeader(SCHEMA_VERSION_HEADER) String schemaVersion,
            @RequestHeader(IDEMPOTENCY_HEADER) String idempotencyKey
    ) {
        return courseImportService.importCourses(
                request,
                CurriculumType.GENERAL_EDUCATION,
                importId,
                schemaVersion,
                idempotencyKey
        );
    }
}
