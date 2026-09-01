package hsu.hanseomate.global.security;

public record IssuedRefreshToken(
        String refreshToken,
        long expiresInSeconds
) {
}
