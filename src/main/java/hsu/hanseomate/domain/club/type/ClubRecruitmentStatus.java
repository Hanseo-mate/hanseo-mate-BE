package hsu.hanseomate.domain.club.type;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모집 상태: ALWAYS_OPEN=상시모집, UPCOMING=모집전, RECRUITING=모집중, CLOSED=모집완료. 상시모집이 우선하며, 그렇지 않고 시작일 또는 종료일이 없으면 CLOSED.")
public enum ClubRecruitmentStatus {
    UPCOMING,
    RECRUITING,
    CLOSED,
    ALWAYS_OPEN;

    public static ClubRecruitmentStatus from(
            LocalDate startDate, LocalDate endDate, LocalDate today, boolean alwaysOpen
    ) {
        if (alwaysOpen) {
            return ALWAYS_OPEN;
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return CLOSED;
        }
        if (today.isBefore(startDate)) {
            return UPCOMING;
        }
        return today.isAfter(endDate) ? CLOSED : RECRUITING;
    }

    public boolean isRecruiting() {
        return this == RECRUITING || this == ALWAYS_OPEN;
    }
}
