package hsu.hanseomate.domain.popup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record PopupNavigationRequest(
        @Schema(
                description = "이동 계약 버전. 현재 1만 지원",
                example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Integer schemaVersion,

        @Schema(
                description = "앱과 합의한 의미 기반 이동 유형",
                example = "NOTICE_DETAIL",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String type,

        @Schema(
                description = "이동 유형별 파라미터. 정적 화면 이동에서는 생략",
                nullable = true
        )
        Map<String, Object> params
) {
}
