package hsu.hanseomate.domain.home.controller;

import hsu.hanseomate.domain.home.dto.HomeMessageRequest;
import hsu.hanseomate.domain.home.dto.HomeMessageResponse;
import hsu.hanseomate.domain.home.service.HomeMessageService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 메인 메시지 관리", description = "ADMIN 권한으로 메인페이지 공통 메시지를 조회하고 설정합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/home-message")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "인증 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "관리자 권한 필요",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminHomeMessageController {

    private final HomeMessageService homeMessageService;

    @Operation(summary = "메인 메시지 조회")
    @GetMapping
    public HomeMessageResponse getMessage() {
        return new HomeMessageResponse(homeMessageService.getMessage());
    }

    @Operation(summary = "메인 메시지 설정",
            description = "공통 메시지 하나를 저장하거나 교체합니다. 앞뒤 공백을 제거하며 빈 문자열은 문구 해제입니다.")
    @ApiResponse(responseCode = "400", description = "메시지 누락, null 또는 500자 초과",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping
    public HomeMessageResponse setMessage(@Valid @RequestBody HomeMessageRequest request) {
        return new HomeMessageResponse(homeMessageService.setMessage(request.message()));
    }
}
