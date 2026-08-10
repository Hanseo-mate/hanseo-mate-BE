package hsu.hanseomate.domain.schoolcalendar.controller;

import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventRequest;
import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventResponse;
import hsu.hanseomate.domain.schoolcalendar.service.SchoolCalendarEventService;
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
        name = "관리자 학교 일정 관리",
        description = "ADMIN 권한으로 학교 공식 일정을 관리합니다."
)
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/school-calendars")
public class AdminSchoolCalendarController {

    private final SchoolCalendarEventService schoolCalendarEventService;

    @Operation(summary = "관리자용 학교 일정 전체 조회")
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
    public List<SchoolCalendarEventResponse> getEvents() {
        return schoolCalendarEventService.getEvents();
    }

    @Operation(summary = "학교 일정 등록")
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
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<SchoolCalendarEventResponse> createEvent(
            @Valid @RequestBody SchoolCalendarEventRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(schoolCalendarEventService.createEvent(request));
    }

    @Operation(summary = "학교 일정 수정")
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
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "학교 일정 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/{calendarId}")
    public SchoolCalendarEventResponse updateEvent(
            @Parameter(description = "학교 일정 ID", required = true)
            @Positive(message = "일정 ID는 1 이상이어야 합니다.")
            @PathVariable Long calendarId,
            @Valid @RequestBody SchoolCalendarEventRequest request
    ) {
        return schoolCalendarEventService.updateEvent(calendarId, request);
    }

    @Operation(summary = "학교 일정 삭제")
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
                    responseCode = "403",
                    description = "관리자 권한 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "학교 일정 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/{calendarId}")
    public ResponseEntity<Void> deleteEvent(
            @Parameter(description = "학교 일정 ID", required = true)
            @Positive(message = "일정 ID는 1 이상이어야 합니다.")
            @PathVariable Long calendarId
    ) {
        schoolCalendarEventService.deleteEvent(calendarId);
        return ResponseEntity.noContent().build();
    }
}
