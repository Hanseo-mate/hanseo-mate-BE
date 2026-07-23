package hsu.hanseomate.domain.notices.controller;

import hsu.hanseomate.domain.notices.dto.NoticeDetailResponse;
import hsu.hanseomate.domain.notices.dto.NoticePageResponse;
import hsu.hanseomate.domain.notices.service.NoticeService;
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

@Tag(name = "공지 조회", description = "공지 목록 및 상세를 조회합니다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notices")
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "카테고리별 공지 목록 조회", description = "공지 타입별로 HOT 우선 정렬 후 최신순으로 페이지 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/categories/{noticeType}")
    public NoticePageResponse getNoticesByCategory(
            @Parameter(description = "공지 타입(academic, general, scholarship, graduate)", required = true)
            @PathVariable String noticeType,
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page
    ) {
        return noticeService.getNoticesByCategory(noticeType, page);
    }

    @Operation(summary = "전체 공지 목록 조회", description = "대학원 공지를 제외하고 HOT 우선, 학사→일반→장학 우선순위로 페이지 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public NoticePageResponse getAllNotices(
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
            int page
    ) {
        return noticeService.getAllNotices(page);
    }

    @Operation(summary = "공지 상세 조회", description = "공지 본문과 첨부파일 목록을 함께 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 공지 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "공지 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{noticeId}")
    public NoticeDetailResponse getNoticeDetail(
            @Parameter(description = "공지 ID", required = true)
            @Positive(message = "공지 ID는 1 이상이어야 합니다.")
            @PathVariable Long noticeId
    ) {
        return noticeService.getNoticeDetail(noticeId);
    }
}
