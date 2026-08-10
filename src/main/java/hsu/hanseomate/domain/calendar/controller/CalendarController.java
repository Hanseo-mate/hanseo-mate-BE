package hsu.hanseomate.domain.calendar.controller;

import hsu.hanseomate.domain.calendar.dto.CalendarEventResponse;
import hsu.hanseomate.domain.calendar.service.CalendarEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "학생회 일정 조회",
        description = "로그인 없이 학생회 일정을 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendars")
public class CalendarController {

    private final CalendarEventService calendarEventService;

    @Operation(
            summary = "학생회 일정 전체 조회",
            description = "학생회 일정을 시작일, 종료일, ID 오름차순으로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public List<CalendarEventResponse> getEvents() {
        return calendarEventService.getEvents();
    }
}
