package hsu.hanseomate.global.security;

import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveUserJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_USER = new OAuth2Error(
            "invalid_token",
            "토큰의 사용자가 존재하지 않습니다.",
            null
    );

    private final UserAccountRepository userAccountRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank()) {
            return OAuth2TokenValidatorResult.failure(INVALID_USER);
        }
        try {
            long userId = Long.parseLong(subject);
            if (userId > 0 && userAccountRepository.existsById(userId)) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException exception) {
            // Invalid subjects are handled as an invalid bearer token below.
        }
        return OAuth2TokenValidatorResult.failure(INVALID_USER);
    }
}
