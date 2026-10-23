package hsu.hanseomate.domain.club.type;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "한국 날짜 기준 모집 상태: UPCOMING=모집전, RECRUITING=모집중, CLOSED=모집완료. 공고/기간이 없으면 CLOSED.")
public enum ClubRecruitmentStatus {
    UPCOMING,
    RECRUITING,
    CLOSED;

    public static ClubRecruitmentStatus from(
            LocalDate startDate, LocalDate endDate, LocalDate today
    ) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return CLOSED;
        }
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        return today.isAfter(endDate) ? CLOSED : RECRUITING;
    }
}
