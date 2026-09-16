package hsu.hanseomate.domain.appsetting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record FestivalFloatingButtonUpdateRequest(
        @NotNull(message = "true 또는 false 값이 필요합니다.")
        @JsonDeserialize(using = StrictBooleanDeserializer.class)
        @Schema(description = "앱 홈 축제 플로팅 버튼의 최종 노출 상태", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean visible
) {
}
