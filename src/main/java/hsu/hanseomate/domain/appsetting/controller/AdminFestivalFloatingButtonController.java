package hsu.hanseomate.domain.appsetting.controller;

import hsu.hanseomate.domain.appsetting.dto.FestivalFloatingButtonResponse;
import hsu.hanseomate.domain.appsetting.dto.FestivalFloatingButtonUpdateRequest;
import hsu.hanseomate.domain.appsetting.service.FestivalFloatingButtonService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 축제 플로팅 버튼 설정", description = "앱 홈의 대동제 바로가기 버튼 노출만 제어합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/settings/festival-floating-button")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 처리 실패",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminFestivalFloatingButtonController {

    private final FestivalFloatingButtonService service;

    @Operation(summary = "축제 플로팅 버튼 노출 설정 조회",
            description = "설정이 없으면 visible=false, updatedAt=null을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<FestivalFloatingButtonResponse> getSetting() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getSetting());
    }

    @Operation(summary = "축제 플로팅 버튼 노출 설정 변경",
            description = "원하는 최종 상태를 저장합니다. 동일 값은 변경 시각과 감사 이력을 갱신하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공 또는 동일 상태"),
            @ApiResponse(responseCode = "400", description = "visible 누락 또는 JSON boolean이 아닌 값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "application/json 요청 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FestivalFloatingButtonResponse> update(
            @Valid @RequestBody FestivalFloatingButtonUpdateRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        Long adminId = Long.valueOf(authentication.getName());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.update(body.visible(), adminId, request.getRemoteAddr())
        );
    }
}
