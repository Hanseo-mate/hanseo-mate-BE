package hsu.hanseomate.domain.courseimport.controller;

import hsu.hanseomate.domain.courseimport.dto.CourseImportResponse;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.service.CourseExcelImportFacade;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import hsu.hanseomate.global.exception.CourseWorkbookErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "강좌 엑셀 수입", description = "전공·교양 엑셀을 분석해 학기 강좌를 일괄 저장합니다.")
@RestController
@RequestMapping(path = "/api/v1/timetables", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CourseExcelImportController {

    private final CourseExcelImportFacade courseExcelImportFacade;

    @Operation(
            summary = "전공 강좌 엑셀 수입",
            description = ".xlsx 또는 .xlsm 전공 시간표를 분석하고 데이터베이스에 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장·검토 필요·중복 처리 결과",
                    content = @Content(schema = @Schema(implementation = CourseImportResponse.class))),
            @ApiResponse(responseCode = "400", description = "파일 누락·형식 오류·손상된 엑셀",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "파일 또는 압축 해제 크기 제한 초과",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "학기·강좌 유형 등 처리 불가능한 파일",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/major", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportResponse importMajorCourses(
            @Parameter(
                    description = "전공 강좌 시간표 엑셀 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("file") MultipartFile file
    ) {
        return courseExcelImportFacade.importWorkbook(file, CurriculumType.MAJOR);
    }

    @Operation(
            summary = "교양 강좌 엑셀 수입",
            description = ".xlsx 또는 .xlsm 교양 시간표를 분석하고 데이터베이스에 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장·검토 필요·중복 처리 결과",
                    content = @Content(schema = @Schema(implementation = CourseImportResponse.class))),
            @ApiResponse(responseCode = "400", description = "파일 누락·형식 오류·손상된 엑셀",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "파일 또는 압축 해제 크기 제한 초과",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "학기·강좌 유형 등 처리 불가능한 파일",
                    content = @Content(schema = @Schema(implementation = CourseWorkbookErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping(path = "/general-education", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CourseImportResponse importGeneralEducationCourses(
            @Parameter(
                    description = "교양 강좌 시간표 엑셀 파일",
                    required = true,
                    schema = @Schema(type = "string", format = "binary")
            )
            @RequestPart("file") MultipartFile file
    ) {
        return courseExcelImportFacade.importWorkbook(file, CurriculumType.GENERAL_EDUCATION);
    }
}
