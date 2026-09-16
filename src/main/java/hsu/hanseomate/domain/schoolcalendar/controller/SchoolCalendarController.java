package hsu.hanseomate.domain.schoolcalendar.controller;

import hsu.hanseomate.domain.schoolcalendar.dto.SchoolCalendarEventResponse;
import hsu.hanseomate.domain.schoolcalendar.service.SchoolCalendarEventService;
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
        name = "학교 일정 조회",
        description = "로그인 없이 학교 공식 일정을 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/calendars/school")
public class SchoolCalendarController {

    private final SchoolCalendarEventService schoolCalendarEventService;

    @Operation(summary = "학교 일정 전체 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping
    public List<SchoolCalendarEventResponse> getEvents() {
        return schoolCalendarEventService.getEvents();
    }
}
