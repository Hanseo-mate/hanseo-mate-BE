package hsu.hanseomate.domain.busschedule.controller;

import hsu.hanseomate.domain.busschedule.dto.BusScheduleResponse;
import hsu.hanseomate.domain.busschedule.service.BusScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "버스 시간표 조회",
        description = "버스 시간표 이미지 목록을 조회합니다. 비로그인 사용자도 접근 가능합니다."
)
@RestController
@RequestMapping("/api/bus-schedules")
@RequiredArgsConstructor
public class BusScheduleController {

    private final BusScheduleService busScheduleService;

    @Operation(
            summary = "버스 시간표 전체 조회",
            description = "DB에 저장된 모든 버스 시간표 이미지 목록을 반환합니다."
    )
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<BusScheduleResponse> getAllSchedules() {
        return busScheduleService.getAllSchedules();
    }
}
