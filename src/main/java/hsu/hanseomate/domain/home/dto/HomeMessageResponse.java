package hsu.hanseomate.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record HomeMessageResponse(
        @Schema(description = "설정된 공통 메인 메시지. 미설정 시 빈 문자열", maxLength = 500)
        String message
) {
}
