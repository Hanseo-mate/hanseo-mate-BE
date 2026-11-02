package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ClubRecruitmentUpdateRequest(
        String recruitmentContent,
        LocalDate recruitmentStartDate,
        LocalDate recruitmentEndDate,

        @Size(max = 2048, message = "모집공고 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String recruitmentUrl,

        @Schema(description = "상시모집 여부. true면 날짜보다 우선하며 생략 또는 null이면 false.", defaultValue = "false")
        Boolean recruitmentAlwaysOpen
) {
    public ClubRecruitmentUpdateRequest {
        recruitmentAlwaysOpen = Boolean.TRUE.equals(recruitmentAlwaysOpen);
    }
}
