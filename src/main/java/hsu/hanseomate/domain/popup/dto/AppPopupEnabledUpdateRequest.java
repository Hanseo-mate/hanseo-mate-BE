package hsu.hanseomate.domain.popup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AppPopupEnabledUpdateRequest(
        @Schema(description = "관리자 노출 활성화 여부")
        @NotNull(message = "활성화 여부는 필수입니다.")
        Boolean enabled
) {
}
