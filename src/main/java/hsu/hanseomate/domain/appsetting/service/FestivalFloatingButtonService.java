package hsu.hanseomate.domain.appsetting.service;

import hsu.hanseomate.domain.appsetting.dto.FestivalFloatingButtonResponse;
import hsu.hanseomate.domain.appsetting.entity.AppFeatureSetting;
import hsu.hanseomate.domain.appsetting.entity.AppFeatureSettingAudit;
import hsu.hanseomate.domain.appsetting.repository.AppFeatureSettingAuditRepository;
import hsu.hanseomate.domain.appsetting.repository.AppFeatureSettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FestivalFloatingButtonService {

    public static final String SETTING_KEY = "FESTIVAL_FLOATING_BUTTON";

    private final AppFeatureSettingRepository settingRepository;
    private final AppFeatureSettingAuditRepository auditRepository;
    private final Clock clock;

    public FestivalFloatingButtonResponse getSetting() {
        return settingRepository.findById(SETTING_KEY)
                .map(FestivalFloatingButtonResponse::from)
                .orElseGet(FestivalFloatingButtonResponse::defaultState);
    }

    @Transactional
    public FestivalFloatingButtonResponse update(boolean visible, Long adminId, String requestIp) {
        settingRepository.ensureDefaultExists(SETTING_KEY);
        AppFeatureSetting setting = settingRepository.findByKeyForUpdate(SETTING_KEY)
                .orElseThrow(() -> new IllegalStateException("축제 버튼 설정을 초기화할 수 없습니다."));
        if (setting.isEnabled() == visible) {
            return FestivalFloatingButtonResponse.from(setting);
        }

        // MySQL DATETIME(6)과 응답 정밀도를 맞춰 재조회/재전송 시 같은 시각을 반환한다.
        Instant changedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean previousEnabled = setting.isEnabled();
        setting.changeEnabled(visible, adminId, changedAt);
        settingRepository.flush();
        auditRepository.saveAndFlush(new AppFeatureSettingAudit(
                SETTING_KEY, adminId, changedAt, previousEnabled, visible, requestIp
        ));
        return FestivalFloatingButtonResponse.from(setting);
    }
}
