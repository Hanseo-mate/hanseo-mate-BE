package hsu.hanseomate.domain.popup.controller;

import hsu.hanseomate.domain.popup.dto.ActiveAppPopupResponse;
import hsu.hanseomate.domain.popup.service.AppPopupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "앱 시작 팝업 조회",
        description = "로그인 여부와 관계없이 현재 노출 중인 앱 시작 팝업을 조회합니다."
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/popups")
public class AppPopupController {

    private final AppPopupService appPopupService;

    @Operation(
            summary = "현재 노출 중인 팝업 조회",
            description = "활성화 및 노출 기간 조건을 만족하는 팝업을 displayOrder와 ID 오름차순으로 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/active")
    public ResponseEntity<List<ActiveAppPopupResponse>> getActivePopups() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(appPopupService.getActivePopups());
    }
}
