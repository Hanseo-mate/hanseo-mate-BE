package hsu.hanseomate.domain.push.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * PUT /api/v1/push-tokens 요청 DTO.
 * 최초 등록 시 로컬 알림 설정을 함께 저장할 수 있습니다.
 */
public record RegisterPushTokenRequest(

        @NotBlank(message = "expoPushToken은 필수입니다.")
        @Size(max = 200, message = "expoPushToken은 200자를 초과할 수 없습니다.")
        String expoPushToken,

        @NotBlank(message = "projectId는 필수입니다.")
        @Size(max = 100, message = "projectId는 100자를 초과할 수 없습니다.")
        String projectId,

        @NotBlank(message = "platform은 필수입니다.")
        @Pattern(regexp = "ios|android", message = "platform은 ios 또는 android여야 합니다.")
        String platform,

        @NotBlank(message = "installationId는 필수입니다.")
        @Size(max = 100, message = "installationId는 100자를 초과할 수 없습니다.")
        String installationId,

        @NotBlank(message = "appVersion은 필수입니다.")
        @Size(max = 20, message = "appVersion은 20자를 초과할 수 없습니다.")
        String appVersion,

        /** 생략하면 저장된 설정을 유지합니다. 새 설치에 저장된 설정이 없으면 전체 ON입니다. */
        Map<String, Object> notificationPreferences
) {
    public RegisterPushTokenRequest(String expoPushToken, String projectId, String platform,
                                    String installationId, String appVersion) {
        this(expoPushToken, projectId, platform, installationId, appVersion, null);
    }
}
