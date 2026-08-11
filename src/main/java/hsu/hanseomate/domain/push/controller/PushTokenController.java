package hsu.hanseomate.domain.push.controller;

import hsu.hanseomate.domain.push.dto.RegisterPushTokenRequest;
import hsu.hanseomate.domain.push.service.PushTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expo Push Token 등록 / 해제 API.
 *
 * <ul>
 *   <li>PUT  /api/v1/push-tokens         — 토큰 등록 또는 갱신 (optional JWT)</li>
 *   <li>DELETE /api/v1/push-tokens/{installationId} — 사용자 연결 해제 (optional JWT)</li>
 * </ul>
 */
@Tag(name = "Push Token", description = "Expo Push Token 등록 및 해제")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-tokens")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    @Operation(
            summary = "Push Token 등록/갱신",
            description = "앱 설치 단위(installationId) 기준으로 토큰을 upsert합니다. "
                    + "로그인 상태라면 JWT에서 userId를 추출하여 함께 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록/갱신 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 필드")
    })
    @PutMapping
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterPushTokenRequest request
    ) {
        Long userId = extractUserId(jwt);
        pushTokenService.registerOrUpdateToken(userId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Push Token 사용자 연결 해제",
            description = "로그아웃 등의 상황에서 user_id 연결을 제거합니다. "
                    + "비로그인 공지 수신이 필요하므로 토큰은 삭제하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "연결 해제 성공")
    })
    @DeleteMapping("/{installationId}")
    public ResponseEntity<Void> deactivatePushToken(
            @Parameter(description = "앱 설치 단위 UUID", required = true)
            @PathVariable String installationId
    ) {
        pushTokenService.unlinkUser(installationId);
        return ResponseEntity.noContent().build();
    }

    /** JWT의 sub 클레임(Long)을 추출합니다. 비로그인이면 null을 반환합니다. */
    private Long extractUserId(Jwt jwt) {
        if (jwt == null) return null;
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) return null;
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
