package hsu.hanseomate.domain.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CalendarEventRequest(
        @NotNull(message = "일정 시작일은 필수입니다.")
        LocalDate startDate,

        @NotNull(message = "일정 종료일은 필수입니다.")
        LocalDate endDate,

        @NotBlank(message = "일정 제목은 필수입니다.")
        @Size(max = 500, message = "일정 제목은 500자 이하여야 합니다.")
        String title
) {
}
