package hsu.hanseomate.domain.busschedule.controller;

import hsu.hanseomate.domain.busschedule.dto.BusScheduleResponse;
import hsu.hanseomate.domain.busschedule.service.BusScheduleService;
import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(
        name = "관리자 버스 시간표 관리",
        description = "ADMIN 권한으로 버스 시간표 이미지를 등록 및 교체합니다."
)
@RestController
@RequestMapping("/api/admin/bus-schedules")
@RequiredArgsConstructor
public class AdminBusScheduleController {

    private final BusScheduleService busScheduleService;

    @Operation(
            summary = "버스 시간표 이미지 업로드/수정",
            description = "대분류와 소분류에 해당하는 버스 시간표 이미지를 업로드합니다. "
                    + "이미 해당 분류의 이미지가 존재하면 기존 파일을 삭제하고 새 이미지로 교체합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드/교체 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "파일 누락 또는 잘못된 이미지",
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
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BusScheduleResponse> uploadOrUpdateSchedule(
            @RequestPart("image") MultipartFile image,
            @RequestParam("mainCategory") MainCategory mainCategory,
            @RequestParam("subCategory") SubCategory subCategory
    ) {
        BusScheduleResponse response =
                busScheduleService.uploadOrUpdateSchedule(image, mainCategory, subCategory);
        return ResponseEntity.ok(response);
    }
}
