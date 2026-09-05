package hsu.hanseomate.domain.appsetting.dto;

import hsu.hanseomate.domain.appsetting.entity.AppFeatureSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FestivalFloatingButtonResponse(
        @Schema(description = "앱 홈 축제 플로팅 버튼 노출 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean visible,
        @Schema(
                description = "노출 상태가 실제로 마지막 변경된 UTC 시각. 변경 이력이 없으면 null",
                type = "string", format = "date-time", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant updatedAt
) {
    public static FestivalFloatingButtonResponse from(AppFeatureSetting setting) {
        return new FestivalFloatingButtonResponse(setting.isEnabled(), setting.getUpdatedAt());
    }

    public static FestivalFloatingButtonResponse defaultState() {
        return new FestivalFloatingButtonResponse(false, null);
    }
}
