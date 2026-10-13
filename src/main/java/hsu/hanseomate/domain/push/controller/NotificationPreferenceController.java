package hsu.hanseomate.domain.push.controller;

import hsu.hanseomate.domain.push.dto.NotificationPreferenceResponse;
import hsu.hanseomate.domain.push.dto.NotificationPreferenceUpdateRequest;
import hsu.hanseomate.domain.push.service.NotificationPreferenceService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Preferences", description = "기기별 전체·종류별 푸시 알림 설정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService service;

    @Operation(summary = "기기별 알림 설정 조회", description = "로그인 불필요. 현재 등록된 Expo 토큰을 X-Push-Token 헤더로 전달합니다.")
    @GetMapping
    public ResponseEntity<NotificationPreferenceResponse> get(
            @RequestParam String installationId, @RequestHeader("X-Push-Token") String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(installationId, token));
    }

    @Operation(summary = "기기별 알림 설정 부분 변경", description = "생략한 설정은 유지합니다. 전체 OFF여도 종류별 선택은 보존됩니다.")
    @PatchMapping
    public ResponseEntity<NotificationPreferenceResponse> update(
            @RequestParam String installationId, @RequestHeader("X-Push-Token") String token,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.update(installationId, token, NotificationPreferenceUpdateRequest.parse(body)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> denied(AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore())
                .body(ApiErrorResponse.of(HttpStatus.FORBIDDEN, exception.getMessage(), request.getRequestURI()));
    }
}
