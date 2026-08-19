package hsu.hanseomate.domain.timetable.composition.currentuser;

import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtCurrentUserIdProvider implements CurrentUserIdProvider {

    private final UserAccountRepository userAccountRepository;

    @Override
    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
        try {
            Long userId = Long.valueOf(authentication.getName());
            if (!userAccountRepository.existsById(userId)) {
                throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}
