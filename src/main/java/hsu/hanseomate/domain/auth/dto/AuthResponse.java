package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.type.UserRole;
import hsu.hanseomate.global.security.IssuedToken;
import java.time.LocalDateTime;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String loginId,
        UserRole role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AuthResponse from(IssuedToken token, UserAccount userAccount) {
        return new AuthResponse(
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                userAccount.getId(),
                userAccount.getLoginId(),
                userAccount.getRole(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt()
        );
    }
}
