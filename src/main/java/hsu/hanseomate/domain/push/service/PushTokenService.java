package hsu.hanseomate.domain.push.service;

import hsu.hanseomate.domain.push.dto.RegisterPushTokenRequest;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push Token 등록/해제 비즈니스 로직.
 * installation_id 기준으로 upsert하며, 동일 토큰을 보유한 다른 기기는 비활성화합니다.
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
        pushDeviceRepository.findByInstallationId(request.installationId())
                .ifPresentOrElse(
                        existing -> updateExistingDevice(existing, userId, request),
                        () -> createNewDevice(userId, request)
                );
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

    private void updateExistingDevice(PushDevice device, Long userId, RegisterPushTokenRequest request) {
        // 토큰이 바뀐 경우, 새 토큰을 보유한 다른 기기를 먼저 비활성화
        if (!device.getExpoPushToken().equals(request.expoPushToken())) {
            deactivateConflictingToken(request.expoPushToken(), device.getInstallationId());
        }
        device.update(userId, request.expoPushToken(), request.projectId(), request.appVersion());
        log.info("Updated push device installationId={}", request.installationId());
    }

    private void createNewDevice(Long userId, RegisterPushTokenRequest request) {
        // 동일 Expo 토큰이 다른 설치 ID로 이미 등록된 경우 비활성화
        deactivateConflictingToken(request.expoPushToken(), null);

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

    private void deactivateConflictingToken(String expoPushToken, String excludeInstallationId) {
        pushDeviceRepository.findByExpoPushToken(expoPushToken)
                .filter(d -> excludeInstallationId == null
                        || !d.getInstallationId().equals(excludeInstallationId))
                .ifPresent(d -> {
                    d.deactivate("TOKEN_REASSIGNED");
                    log.info("Deactivated conflicting device id={} (token reassigned)", d.getId());
                });
    }
}
