package hsu.hanseomate.global.security;

public record IssuedToken(
        String accessToken,
        long expiresInSeconds
) {
}
