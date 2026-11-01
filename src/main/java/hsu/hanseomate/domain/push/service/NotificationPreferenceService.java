package hsu.hanseomate.domain.push.service;

import hsu.hanseomate.domain.push.dto.NotificationPreferenceResponse;
import hsu.hanseomate.domain.push.dto.NotificationPreferenceUpdateRequest;
import hsu.hanseomate.domain.push.entity.NotificationPreference;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.NotificationPreferenceRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {
    private final PushDeviceRepository deviceRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse get(String installationId, String token) {
        validate(installationId, token);
        PushDevice device = verify(deviceRepository.findByInstallationId(installationId)
                .orElseThrow(NotificationPreferenceService::denied), token);
        return NotificationPreferenceResponse.from(preferenceRepository.findById(device.getInstallationId())
                .orElseGet(() -> NotificationPreference.defaults(device.getInstallationId())));
    }

    @Transactional
    public NotificationPreferenceResponse update(
            String installationId, String token, NotificationPreferenceUpdateRequest request) {
        validate(installationId, token);
        // 기존 기기 행을 잠가 최초 설정 생성 및 동시에 들어온 부분 갱신을 직렬화합니다.
        PushDevice device = verify(deviceRepository.findByInstallationIdForUpdate(installationId)
                .orElseThrow(NotificationPreferenceService::denied), token);
        NotificationPreference preference = preferenceRepository.findById(device.getInstallationId())
                .orElseGet(() -> NotificationPreference.defaults(device.getInstallationId()));
        preference.update(request.enabled(), request.categories());
        return NotificationPreferenceResponse.from(preferenceRepository.save(preference));
    }

    private static PushDevice verify(PushDevice device, String token) {
        if (!MessageDigest.isEqual(device.getExpoPushToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) throw denied();
        return device;
    }

    private static void validate(String installationId, String token) {
        if (installationId == null || installationId.isBlank() || installationId.length() > 100
                || token == null || token.isBlank() || token.length() > 200) {
            throw new BadRequestException("installationId와 X-Push-Token을 올바르게 지정해야 합니다.");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("등록된 기기 정보를 확인할 수 없습니다.");
    }
}
