package hsu.hanseomate.domain.studentcouncilnotice.controller;

import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeDetailResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticePageResponse;
import hsu.hanseomate.domain.studentcouncilnotice.service.StudentCouncilNoticeService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "학생회 공지 조회", description = "학생회에서 직접 작성한 공지를 조회합니다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices/categories/admin")
public class StudentCouncilNoticeController {

    private final StudentCouncilNoticeService studentCouncilNoticeService;

    @Operation(
            summary = "학생회 공지 목록 조회",
            description = "학생회 공지와 현재 조회수를 최신 작성순으로 10개씩 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 페이지 번호",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public StudentCouncilNoticePageResponse getNotices(
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page
    ) {
        return studentCouncilNoticeService.getNotices(page);
    }

    @Operation(
            summary = "학생회 공지 상세 조회",
            description = "학생회 공지를 조회하고 조회수를 1 증가시킨 뒤 최신 조회수를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 공지 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "학생회 공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{noticeId}")
    public StudentCouncilNoticeDetailResponse getNotice(
            @Parameter(description = "학생회 공지 ID", required = true)
            @Positive(message = "공지 ID는 1 이상이어야 합니다.")
            @PathVariable Long noticeId
    ) {
        return studentCouncilNoticeService.getNoticeAndIncrementViewCount(noticeId);
    }
}
