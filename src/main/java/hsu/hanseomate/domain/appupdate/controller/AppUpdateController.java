package hsu.hanseomate.domain.appupdate.controller;

import hsu.hanseomate.domain.appupdate.dto.AppUpdateResponses.Check;
import hsu.hanseomate.domain.appupdate.service.AppUpdatePolicyService;
import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "앱 업데이트 확인", description = "로그인 없이 플랫폼별 네이티브 빌드로 업데이트 필요 여부를 판정합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/app-updates")
public class AppUpdateController {
    private final AppUpdatePolicyService service;

    @Operation(summary = "앱 업데이트 필요 여부 확인", description = "200 응답의 action은 NONE, OPTIONAL, REQUIRED입니다. 표시 버전은 비교에 사용하지 않습니다.")
    @ApiResponse(responseCode = "200", description = "업데이트 판정 성공",
            content = @Content(schema = @Schema(implementation = Check.class)))
    @ApiResponse(responseCode = "400", description = "플랫폼, 빌드 또는 표시 버전 오류",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/check")
    public Check check(@RequestParam AppPlatform platform, @RequestParam String build, @RequestParam String version) {
        if (!build.matches("[0-9]{1,19}")) {
            throw hsu.hanseomate.domain.appupdate.exception.AppUpdateException.badRequest("build: 1 이상의 십진 정수가 필요합니다.");
        }
        try {
            return service.check(platform, Long.valueOf(build), version);
        } catch (NumberFormatException exception) {
            throw hsu.hanseomate.domain.appupdate.exception.AppUpdateException.badRequest("build: BIGINT 범위를 초과했습니다.");
        }
    }
}
