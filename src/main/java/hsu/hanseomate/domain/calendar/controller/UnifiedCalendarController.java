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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "통합 일정 조회",
        description = "학교·학생회·개인 일정을 하나로 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendars/all")
public class UnifiedCalendarController {

    private final UnifiedCalendarService unifiedCalendarService;

    @Operation(summary = "통합 일정 전체 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "전달한 토큰이 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<UnifiedCalendarEventResponse> getEvents(
            Authentication authentication
    ) {
        return unifiedCalendarService.getEvents(optionalCurrentUserId(authentication));
    }

    private Optional<Long> optionalCurrentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(authentication.getName()));
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}
