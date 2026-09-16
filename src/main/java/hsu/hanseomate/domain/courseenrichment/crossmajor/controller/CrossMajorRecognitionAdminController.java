package hsu.hanseomate.domain.courseenrichment.crossmajor.controller;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionImportResponse;
import hsu.hanseomate.domain.courseenrichment.crossmajor.service.CrossMajorRecognitionImportFacade;
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

@Tag(name = "타학과 전공인정 관리", description = "연도별 타학과 전공인정 엑셀 스냅샷을 수입합니다.")
@RestController
@RequestMapping(
        path = "/api/admin/course-enrichments/cross-major-recognitions",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class CrossMajorRecognitionAdminController {

    private final CrossMajorRecognitionImportFacade importFacade;

    @Operation(
            summary = "타학과 전공인정 엑셀 수입",
            description = "파일명·문서 제목·시트명에서 정책연도와 업로드 학기를 감지해 연간 활성 스냅샷을 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장·중복·검토 필요 처리 결과",
                    content = @Content(schema = @Schema(
                            implementation = CrossMajorRecognitionImportResponse.class))),
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
            @ApiResponse(responseCode = "422", description = "정책연도·학기 또는 원본 시트 감지 실패·충돌",
                    content = @Content(schema = @Schema(
                            implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CrossMajorRecognitionImportResponse importWorkbook(
            @Parameter(
                    description = "타학과 전공인정 교과목 목록 .xlsx 또는 .xlsm 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("file") MultipartFile file
    ) {
        return importFacade.importWorkbook(file);
    }
}
