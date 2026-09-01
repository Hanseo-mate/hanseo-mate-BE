package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.global.security.IssuedRefreshToken;
import hsu.hanseomate.global.security.IssuedToken;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshTokenExpiresIn
) {

    public static TokenRefreshResponse from(
            IssuedToken accessToken,
            IssuedRefreshToken refreshToken
    ) {
        return new TokenRefreshResponse(
                accessToken.accessToken(),
                refreshToken.refreshToken(),
                "Bearer",
                accessToken.expiresInSeconds(),
                refreshToken.expiresInSeconds()
        );
    }
}
