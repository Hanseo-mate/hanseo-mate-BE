package hsu.hanseomate.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HomeMessageRequest(
        @Schema(description = "공통 메인 메시지. 빈 문자열로 설정하면 표시할 문구를 지웁니다.",
                example = "오늘도 한서메이트와 함께 좋은 하루 보내세요!", maxLength = 500)
        @NotNull(message = "메시지는 필수입니다. 문구를 지우려면 빈 문자열을 입력해주세요.")
        @Size(max = 500, message = "메시지는 500자 이하여야 합니다.")
        String message
) {
}
