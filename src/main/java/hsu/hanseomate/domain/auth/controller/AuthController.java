package hsu.hanseomate.domain.auth.controller;

import hsu.hanseomate.domain.auth.dto.AuthResponse;
import hsu.hanseomate.domain.auth.dto.CafeteriaPreferenceUpdateRequest;
import hsu.hanseomate.domain.auth.dto.LoginRequest;
import hsu.hanseomate.domain.auth.dto.MyPageResponse;
import hsu.hanseomate.domain.auth.dto.RefreshTokenRequest;
import hsu.hanseomate.domain.auth.dto.SignupRequest;
import hsu.hanseomate.domain.auth.dto.TokenRefreshResponse;
import hsu.hanseomate.domain.auth.dto.WithdrawalRequest;
import hsu.hanseomate.domain.auth.service.AuthService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(201).body(authService.signup(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(
            summary = "Access Token 재발급",
            description = "유효한 Refresh Token을 새 토큰 쌍으로 교체합니다. "
                    + "재발급에 사용한 Refresh Token은 즉시 폐기됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "재발급 성공",
                    content = @Content(schema = @Schema(
                            implementation = TokenRefreshResponse.class
                    ))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 누락 또는 잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않거나 만료 또는 폐기된 Refresh Token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/refresh")
    public TokenRefreshResponse refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return authService.refresh(request);
    }

    @Operation(
            summary = "로그아웃",
            description = "전달한 Refresh Token을 폐기합니다. "
                    + "이미 폐기되었거나 존재하지 않는 토큰도 동일하게 성공 처리합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 처리 완료"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 누락 또는 잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "내 정보, 동아리 후기 및 좋아요 목록 조회",
            description = "로그인 사용자의 계정 정보, 본인이 작성한 동아리 후기, 좋아요한 동아리 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = MyPageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 유효하지 않은 토큰",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/me")
    public MyPageResponse getMyPage(Authentication authentication) {
        return authService.getMyPage(currentUserId(authentication));
    }

    @Operation(
            summary = "선호 학생식당 설정",
            description = "로그인 사용자의 선호 학생식당을 서산 또는 태안 학생식당으로 설정합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "설정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "누락되었거나 지원하지 않는 식당 값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요 또는 유효하지 않은 토큰",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/me/cafeteria-preference")
    public ResponseEntity<Void> updateCafeteriaPreference(
            Authentication authentication,
            @Valid @RequestBody CafeteriaPreferenceUpdateRequest request
    ) {
        authService.updateCafeteriaPreference(
                currentUserId(authentication),
                request
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "현재 비밀번호를 확인한 뒤 로그인 계정과 모든 귀속 데이터를 영구 삭제합니다. "
                    + "삭제된 계정과 데이터는 복구되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "회원 탈퇴 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 누락 또는 잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요, 유효하지 않은 토큰 또는 비밀번호 불일치",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            Authentication authentication,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        authService.withdraw(currentUserId(authentication), request);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}
