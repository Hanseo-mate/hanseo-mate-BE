package hsu.hanseomate.domain.calendar.controller;

import hsu.hanseomate.domain.calendar.dto.UnifiedCalendarEventResponse;
import hsu.hanseomate.domain.calendar.service.UnifiedCalendarService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "관리자 전체 공개 일정 조회",
        description = "ADMIN 권한으로 일반 사용자에게 공개되는 학교·학생회 일정을 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/calendars/all")
public class AdminUnifiedCalendarController {

    private final UnifiedCalendarService unifiedCalendarService;

    @Operation(
            summary = "관리자용 전체 공개 일정 조회",
            description = "학교 공식 일정과 학생회 일정을 통합 조회하며 개인 일정은 포함하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<UnifiedCalendarEventResponse> getEvents() {
        return unifiedCalendarService.getPublicEvents();
    }
}
