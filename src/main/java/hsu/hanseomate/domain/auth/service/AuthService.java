package hsu.hanseomate.domain.auth.service;

import hsu.hanseomate.domain.auth.dto.AuthResponse;
import hsu.hanseomate.domain.auth.dto.LoginRequest;
import hsu.hanseomate.domain.auth.dto.MyPageResponse;
import hsu.hanseomate.domain.auth.dto.SignupRequest;
import hsu.hanseomate.domain.auth.exception.DuplicateLoginIdException;
import hsu.hanseomate.domain.auth.exception.InvalidCredentialsException;
import hsu.hanseomate.domain.club.repository.ClubLikeRepository;
import hsu.hanseomate.domain.club.repository.ClubReviewRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.security.JwtTokenProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final ClubReviewRepository clubReviewRepository;
    private final ClubLikeRepository clubLikeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        if (userAccountRepository.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException();
        }

        UserAccount userAccount = UserAccount.create(
                loginId,
                passwordEncoder.encode(request.password())
        );
        try {
            UserAccount savedUserAccount = userAccountRepository.saveAndFlush(userAccount);
            return AuthResponse.from(
                    jwtTokenProvider.issue(savedUserAccount),
                    savedUserAccount
            );
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateLoginIdException();
        }
    }

    public AuthResponse login(LoginRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        UserAccount userAccount = userAccountRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return AuthResponse.from(jwtTokenProvider.issue(userAccount), userAccount);
    }

    public MyPageResponse getMyPage(Long userId) {
        UserAccount userAccount = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "로그인이 필요합니다."
                ));
        return MyPageResponse.from(
                userAccount,
                clubReviewRepository.findAllByReviewerIdWithClubAndReviewTags(userId),
                clubLikeRepository.findAllByLikerIdWithClubOrderByIdDesc(userId)
        );
    }

    private String normalizeLoginId(String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }
}
