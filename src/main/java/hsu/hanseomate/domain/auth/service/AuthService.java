package hsu.hanseomate.domain.auth.service;

import hsu.hanseomate.domain.auth.dto.AuthResponse;
import hsu.hanseomate.domain.auth.dto.CafeteriaPreferenceUpdateRequest;
import hsu.hanseomate.domain.auth.dto.LoginRequest;
import hsu.hanseomate.domain.auth.dto.MyPageResponse;
import hsu.hanseomate.domain.auth.dto.RefreshTokenRequest;
import hsu.hanseomate.domain.auth.dto.SignupRequest;
import hsu.hanseomate.domain.auth.dto.TokenRefreshResponse;
import hsu.hanseomate.domain.auth.dto.WithdrawalRequest;
import hsu.hanseomate.domain.auth.exception.DuplicateLoginIdException;
import hsu.hanseomate.domain.auth.exception.InvalidCredentialsException;
import hsu.hanseomate.domain.auth.exception.InvalidRefreshTokenException;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.club.repository.ClubLikeRepository;
import hsu.hanseomate.domain.club.repository.ClubReviewRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.repository.PushTicketRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.security.JwtTokenProvider;
import hsu.hanseomate.global.security.IssuedRefreshToken;
import hsu.hanseomate.global.security.IssuedToken;
import java.util.List;
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
    private final TimetableCourseRepository timetableCourseRepository;
    private final TimetableRepository timetableRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushTicketRepository pushTicketRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

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
        UserAccount savedUserAccount;
        try {
            savedUserAccount = userAccountRepository.saveAndFlush(userAccount);
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateLoginIdException();
        }
        return issueAuthentication(savedUserAccount);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        UserAccount userAccount = userAccountRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return issueAuthentication(userAccount);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(
                request.refreshToken()
        );
        return TokenRefreshResponse.from(
                jwtTokenProvider.issue(rotation.userAccount()),
                rotation.refreshToken()
        );
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
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

    @Transactional
    public void updateCafeteriaPreference(
            Long userId,
            CafeteriaPreferenceUpdateRequest request
    ) {
        RestaurantType preferredRestaurantType =
                request.preferredRestaurantType();
        if (preferredRestaurantType != RestaurantType.MAIN_STUDENT
                && preferredRestaurantType != RestaurantType.TAEAN_STUDENT) {
            throw new BadRequestException(
                    "preferredRestaurantType은 MAIN_STUDENT 또는 "
                            + "TAEAN_STUDENT만 사용할 수 있습니다."
            );
        }

        UserAccount userAccount = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "로그인이 필요합니다."
                ));
        userAccount.changePreferredRestaurantType(preferredRestaurantType);
    }

    @Transactional
    public void withdraw(Long userId, WithdrawalRequest request) {
        UserAccount userAccount = userAccountRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "로그인이 필요합니다."
                ));
        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        timetableCourseRepository.deleteAllByTimetableOwnerId(userId);
        timetableRepository.deleteAllByOwnerId(userId);

        List<Long> pushDeviceIds = pushDeviceRepository.findIdsByUserId(userId);
        if (!pushDeviceIds.isEmpty()) {
            pushTicketRepository.deleteAllByPushDeviceIdIn(pushDeviceIds);
        }
        pushDeviceRepository.deleteAllByUserId(userId);

        refreshTokenService.deleteAllByUserId(userId);
        userAccountRepository.delete(userAccount);
        userAccountRepository.flush();
    }

    private AuthResponse issueAuthentication(UserAccount userAccount) {
        IssuedToken accessToken = jwtTokenProvider.issue(userAccount);
        IssuedRefreshToken refreshToken = refreshTokenService.issue(userAccount);
        return AuthResponse.from(accessToken, refreshToken, userAccount);
    }

    private String normalizeLoginId(String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }
}
