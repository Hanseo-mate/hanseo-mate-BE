package hsu.hanseomate.domain.popup.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.popup.model.PopupNavigation;
import hsu.hanseomate.domain.popup.type.PopupNavigationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record PopupNavigationResponse(
        @Schema(description = "이동 계약 버전", example = "1")
        int schemaVersion,

        @Schema(description = "앱과 합의한 의미 기반 이동 유형")
        PopupNavigationType type,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "이동 유형별 파라미터", nullable = true)
        Map<String, Object> params
) {

    public static PopupNavigationResponse from(PopupNavigation navigation) {
        if (navigation == null) {
            return null;
        }
        return new PopupNavigationResponse(
                navigation.schemaVersion(),
                navigation.type(),
                navigation.params()
        );
    }
}
