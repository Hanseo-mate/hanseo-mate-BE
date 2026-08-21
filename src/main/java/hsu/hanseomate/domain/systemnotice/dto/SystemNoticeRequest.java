package hsu.hanseomate.domain.systemnotice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemNoticeRequest(
        @Schema(description = "시스템 공지 제목", maxLength = 500)
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 500, message = "제목은 500자 이하여야 합니다.")
        String title,

        @Schema(description = "시스템 공지 내용", maxLength = 100000)
        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 100000, message = "내용은 100,000자 이하여야 합니다.")
        String content
) {
}
