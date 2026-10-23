package hsu.hanseomate.domain.club.dto;

import java.time.LocalDate;

public record ClubRecruitmentUpdateRequest(
        String recruitmentContent,
        LocalDate recruitmentStartDate,
        LocalDate recruitmentEndDate
) {
}
