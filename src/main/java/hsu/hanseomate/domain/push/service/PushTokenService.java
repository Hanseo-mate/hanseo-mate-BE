package hsu.hanseomate.domain.push.service;

import hsu.hanseomate.domain.push.dto.RegisterPushTokenRequest;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push Token 등록/해제 비즈니스 로직.
 * installation_id와 Expo token을 함께 식별 기준으로 사용하여 upsert합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserAccountRepository userAccountRepository;

    /**
     * 토큰 등록 또는 갱신 (upsert).
     *
     * @param userId  JWT에서 추출한 사용자 ID (비로그인 시 null)
     * @param request 앱에서 전달한 토큰 정보
     */
    @Transactional
    public void registerOrUpdateToken(Long userId, RegisterPushTokenRequest request) {
        requireExistingUserWhenAuthenticated(userId);
        List<PushDevice> candidates = pushDeviceRepository.findRegistrationCandidatesForUpdate(
                request.installationId(),
                request.expoPushToken()
        );
        PushDevice installationMatch = findByInstallationId(candidates, request.installationId());
        PushDevice tokenMatch = findByExpoPushToken(candidates, request.expoPushToken());

        if (installationMatch != null) {
            updateExistingInstallation(installationMatch, tokenMatch, userId, request);
            return;
        }
        if (tokenMatch != null) {
            reassignExistingToken(tokenMatch, userId, request);
            return;
        }
        createNewDevice(userId, request);
    }

    private void requireExistingUserWhenAuthenticated(Long userId) {
        if (userId != null && !userAccountRepository.existsById(userId)) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
    }

    /**
     * 로그아웃 등 시 user_id 연결을 해제합니다.
     * 비로그인 상태에서도 공지 알림을 수신할 수 있도록 토큰은 삭제하지 않습니다.
     *
     * @param installationId 기기 고유 설치 UUID
     */
    @Transactional
    public void unlinkUser(String installationId) {
        pushDeviceRepository.findByInstallationId(installationId)
                .ifPresentOrElse(
                        device -> {
                            device.clearUserId();
                            log.info("Unlinked user from device installationId={}", installationId);
                        },
                        () -> log.warn("unlinkUser: device not found installationId={}", installationId)
                );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateExistingInstallation(
            PushDevice installationMatch,
            PushDevice tokenMatch,
            Long userId,
            RegisterPushTokenRequest request
    ) {
        if (tokenMatch != null && !Objects.equals(installationMatch.getId(), tokenMatch.getId())) {
            Long conflictingDeviceId = tokenMatch.getId();
            pushDeviceRepository.delete(tokenMatch);
            // UNIQUE(expo_push_token)을 해제한 다음 현재 installation 행에 토큰을 적용합니다.
            pushDeviceRepository.flush();
            log.info("Removed conflicting push device id={} before token reassignment",
                    conflictingDeviceId);
        }

        installationMatch.refreshRegistration(
                userId,
                request.expoPushToken(),
                request.platform(),
                request.projectId(),
                request.appVersion()
        );
        log.info("Updated push device installationId={}", request.installationId());
    }

    private void reassignExistingToken(
            PushDevice device,
            Long userId,
            RegisterPushTokenRequest request
    ) {
        device.reassignInstallation(
                userId,
                request.installationId(),
                request.platform(),
                request.projectId(),
                request.appVersion()
        );
        log.info("Reassigned push device id={} to installationId={}",
                device.getId(), request.installationId());
    }

    private void createNewDevice(Long userId, RegisterPushTokenRequest request) {
        PushDevice device = PushDevice.create(
                userId,
                request.installationId(),
                request.expoPushToken(),
                request.platform(),
                request.projectId(),
                request.appVersion()
        );
        pushDeviceRepository.save(device);
        log.info("Registered new push device installationId={}", request.installationId());
    }

    private PushDevice findByInstallationId(List<PushDevice> candidates, String installationId) {
        return candidates.stream()
                .filter(device -> device.getInstallationId().equals(installationId))
                .findFirst()
                .orElse(null);
    }

    private PushDevice findByExpoPushToken(List<PushDevice> candidates, String expoPushToken) {
        return candidates.stream()
                .filter(device -> device.getExpoPushToken().equals(expoPushToken))
                .findFirst()
                .orElse(null);
    }
}
