package hsu.hanseomate.domain.personalcalendar.controller;

import hsu.hanseomate.domain.personalcalendar.dto.PersonalCalendarEventRequest;
import hsu.hanseomate.domain.personalcalendar.dto.PersonalCalendarEventResponse;
import hsu.hanseomate.domain.personalcalendar.service.PersonalCalendarEventService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "내 일정 관리",
        description = "로그인 사용자가 자신의 일정을 조회, 등록, 수정, 삭제합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendars/me")
public class PersonalCalendarController {

    private final PersonalCalendarEventService personalCalendarEventService;

    @Operation(summary = "내 일정 전체 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<PersonalCalendarEventResponse> getEvents(Authentication authentication) {
        return personalCalendarEventService.getEvents(currentUserId(authentication));
    }

    @Operation(summary = "내 일정 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<PersonalCalendarEventResponse> createEvent(
            Authentication authentication,
            @Valid @RequestBody PersonalCalendarEventRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personalCalendarEventService.createEvent(
                        currentUserId(authentication),
                        request
                ));
    }

    @Operation(summary = "내 일정 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 일정 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "본인 일정 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/{calendarId}")
    public PersonalCalendarEventResponse updateEvent(
            Authentication authentication,
            @Parameter(description = "내 일정 ID", required = true)
            @Positive(message = "일정 ID는 1 이상이어야 합니다.")
            @PathVariable Long calendarId,
            @Valid @RequestBody PersonalCalendarEventRequest request
    ) {
        return personalCalendarEventService.updateEvent(
                currentUserId(authentication),
                calendarId,
                request
        );
    }

    @Operation(summary = "내 일정 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 일정 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "본인 일정 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> deleteEvent(
            Authentication authentication,
            @Parameter(description = "내 일정 ID", required = true)
            @Positive(message = "일정 ID는 1 이상이어야 합니다.")
            @PathVariable Long calendarId
    ) {
        personalCalendarEventService.deleteEvent(
                currentUserId(authentication),
                calendarId
        );
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException(
                    "로그인이 필요합니다."
            );
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}
