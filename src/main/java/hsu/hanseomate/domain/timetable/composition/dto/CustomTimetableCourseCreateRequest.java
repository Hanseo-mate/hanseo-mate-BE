package hsu.hanseomate.domain.timetable.composition.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;

public record CustomTimetableCourseCreateRequest(
        @NotBlank(message = "과목명은 필수입니다.")
        @Size(max = 255, message = "과목명은 255자 이하여야 합니다.")
        String courseName,

        @NotNull(message = "학점은 필수입니다.")
        @DecimalMin(value = "0.001", message = "학점은 0보다 커야 합니다.")
        @DecimalMax(value = "20.000", message = "한 과목의 학점은 20 이하여야 합니다.")
        @Digits(integer = 2, fraction = 3, message = "학점은 소수 셋째 자리까지 입력할 수 있습니다.")
        BigDecimal credit,

        @NotNull(message = "요일은 필수입니다.")
        DayOfWeek dayOfWeek,

        @NotNull(message = "시작 시간은 필수입니다.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "종료 시간은 필수입니다.")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime
) {
}
