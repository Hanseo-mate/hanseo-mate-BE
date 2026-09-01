package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.type.UserRole;
import hsu.hanseomate.global.security.IssuedRefreshToken;
import hsu.hanseomate.global.security.IssuedToken;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn,
        Long userId,
        String loginId,
        UserRole role,
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType preferredRestaurantType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AuthResponse from(
            IssuedToken accessToken,
            IssuedRefreshToken refreshToken,
            UserAccount userAccount
    ) {
        return new AuthResponse(
                accessToken.accessToken(),
                refreshToken.refreshToken(),
                "Bearer",
                accessToken.expiresInSeconds(),
                refreshToken.expiresInSeconds(),
                userAccount.getId(),
                userAccount.getLoginId(),
                userAccount.getRole(),
                userAccount.getPreferredRestaurantType(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt()
        );
    }
}
