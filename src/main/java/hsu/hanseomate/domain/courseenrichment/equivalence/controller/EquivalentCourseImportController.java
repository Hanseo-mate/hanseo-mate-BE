package hsu.hanseomate.domain.courseenrichment.equivalence.controller;

import hsu.hanseomate.domain.courseenrichment.equivalence.dto.EquivalentCourseImportResponse;
import hsu.hanseomate.domain.courseenrichment.equivalence.service.EquivalentCourseImportFacade;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import hsu.hanseomate.global.exception.CourseWorkbookErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "동일교과목 관리", description = "학기별 동일교과목 엑셀 스냅샷을 수입합니다.")
@RestController
@RequestMapping(
        path = "/api/admin/course-enrichments/equivalent-courses/imports",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class EquivalentCourseImportController {

    private final EquivalentCourseImportFacade importFacade;

    @Operation(
            summary = "동일교과목 엑셀 수입",
            description = "파일명·시트명·셀에서 학년도와 학기를 감지해 활성 스냅샷을 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장·중복·검토 필요 처리 결과",
                    content = @Content(schema = @Schema(
                            implementation = EquivalentCourseImportResponse.class))),
            @ApiResponse(responseCode = "400", description = "파일 누락·형식 오류",
                    content = @Content(schema = @Schema(
                            implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "업로드 크기 제한 초과",
                    content = @Content(schema = @Schema(
                            implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "학년도·학기 감지 실패 또는 충돌",
                    content = @Content(schema = @Schema(
                            implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EquivalentCourseImportResponse importEquivalentCourses(
            @Parameter(
                    description = "동일교과목 현황 .xlsx 또는 .xlsm 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("file") MultipartFile file
    ) {
        return importFacade.importWorkbook(file);
    }
}
