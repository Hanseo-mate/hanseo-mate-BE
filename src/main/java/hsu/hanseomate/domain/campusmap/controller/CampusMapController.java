package hsu.hanseomate.domain.campusmap.controller;

import hsu.hanseomate.domain.campusmap.dto.CampusMapTodayResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusMapWeeklyResponse;
import hsu.hanseomate.domain.campusmap.service.CampusMapService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "캠퍼스 맵",
        description = "로그인 사용자의 현재 학기 시간표에 포함된 강의실 위치를 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timetables")
public class CampusMapController {

    private final CampusMapService campusMapService;

    @Operation(
            summary = "오늘 수업 강의실 위치 조회",
            description = "한국 시간 기준 오늘 수업을 조회하며, 좌표를 확인할 수 없는 강의실도 상태와 함께 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/today-locations")
    public CampusMapTodayResponse getTodayLocations() {
        return campusMapService.getTodayLocations();
    }

    @Operation(
            summary = "월요일부터 목요일까지 수업 강의실 위치 조회",
            description = "현재 학기의 수업을 월요일부터 목요일까지 요일별로 묶어 반환합니다. "
                    + "수업이 없는 요일도 빈 배열로 포함합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요하거나 토큰이 유효하지 않음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/weekly-locations")
    public CampusMapWeeklyResponse getWeeklyLocations() {
        return campusMapService.getWeeklyLocations();
    }
}
