package hsu.hanseomate.global.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long accessTokenExpirationSeconds,
        long refreshTokenExpirationSeconds
) {

    private static final int MINIMUM_HS256_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HS256_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank.");
        }
        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT access token expiration must be greater than zero."
            );
        }
        if (refreshTokenExpirationSeconds <= 0) {
            throw new IllegalArgumentException(
                    "Refresh token expiration must be greater than zero."
            );
        }
    }
}
