package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ClubCreateRequest(
        @NotBlank(message = "동아리명은 필수입니다.")
        @Size(max = 100, message = "동아리명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "분과는 필수입니다.")
        @Size(max = 30, message = "분과는 30자 이하여야 합니다.")
        String category,

        String recruitmentContent,
        LocalDate recruitmentStartDate,
        LocalDate recruitmentEndDate,

        @Size(max = 2048, message = "모집공고 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String recruitmentUrl,

        @Schema(description = "상시모집 여부. true면 날짜보다 우선하며 생략 또는 null이면 false.", defaultValue = "false")
        Boolean recruitmentAlwaysOpen
) {
    public ClubCreateRequest {
        recruitmentAlwaysOpen = Boolean.TRUE.equals(recruitmentAlwaysOpen);
    }
}
