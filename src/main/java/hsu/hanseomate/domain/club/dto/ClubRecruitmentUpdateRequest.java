package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ClubRecruitmentUpdateRequest(
        String recruitmentContent,
        LocalDate recruitmentStartDate,
        LocalDate recruitmentEndDate,

        @Size(max = 2048, message = "모집공고 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String recruitmentUrl
) {
}
