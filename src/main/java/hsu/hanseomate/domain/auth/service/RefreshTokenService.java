package hsu.hanseomate.domain.auth.service;

import hsu.hanseomate.domain.auth.entity.RefreshToken;
import hsu.hanseomate.domain.auth.exception.InvalidRefreshTokenException;
import hsu.hanseomate.domain.auth.repository.RefreshTokenRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.global.security.IssuedRefreshToken;
import hsu.hanseomate.global.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final ZoneId APPLICATION_TIME_ZONE = ZoneId.of("Asia/Seoul");

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedRefreshToken issue(UserAccount userAccount) {
        GeneratedToken generated = generateToken();
        LocalDateTime expiresAt = now().plusSeconds(
                jwtProperties.refreshTokenExpirationSeconds()
        );
        refreshTokenRepository.save(RefreshToken.create(
                userAccount,
                generated.hash(),
                UUID.randomUUID().toString(),
                expiresAt
        ));
        return issued(generated.raw(), jwtProperties.refreshTokenExpirationSeconds());
    }

    public Rotation rotate(String rawRefreshToken) {
        LocalDateTime now = now();
        RefreshToken currentToken = refreshTokenRepository
                .findByTokenHashForUpdate(hash(rawRefreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!currentToken.isUsableAt(now)) {
            if (currentToken.wasRotated()) {
                refreshTokenRepository.revokeActiveFamily(
                        currentToken.getFamilyId(),
                        now
                );
            }
            throw new InvalidRefreshTokenException();
        }

        GeneratedToken replacement = generateToken();
        currentToken.revoke(now, replacement.hash());
        refreshTokenRepository.save(RefreshToken.create(
                currentToken.getUserAccount(),
                replacement.hash(),
                currentToken.getFamilyId(),
                currentToken.getExpiresAt()
        ));
        long remainingSeconds = Duration.between(
                now,
                currentToken.getExpiresAt()
        ).getSeconds();
        return new Rotation(
                currentToken.getUserAccount(),
                issued(replacement.raw(), remainingSeconds)
        );
    }

    public void revoke(String rawRefreshToken) {
        String tokenHash;
        try {
            tokenHash = hash(rawRefreshToken);
        } catch (InvalidRefreshTokenException exception) {
            return;
        }
        LocalDateTime now = now();
        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(refreshToken -> refreshTokenRepository.revokeActiveFamily(
                        refreshToken.getFamilyId(),
                        now
                ));
    }

    public void deleteAllByUserId(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    public int deleteExpiredTokens() {
        return refreshTokenRepository.deleteExpiredAtOrBefore(now());
    }

    private IssuedRefreshToken issued(
            String rawRefreshToken,
            long expiresInSeconds
    ) {
        return new IssuedRefreshToken(
                rawRefreshToken,
                expiresInSeconds
        );
    }

    private GeneratedToken generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(raw, hash(raw));
    }

    private String hash(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(
                    rawRefreshToken.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh Token 해시를 생성할 수 없습니다.", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), APPLICATION_TIME_ZONE);
    }

    public record Rotation(
            UserAccount userAccount,
            IssuedRefreshToken refreshToken
    ) {
    }

    private record GeneratedToken(String raw, String hash) {
    }
}
