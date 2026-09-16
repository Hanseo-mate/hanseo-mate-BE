package hsu.hanseomate.domain.timetable.composition.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.global.validation.WholeNumber;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
        @DecimalMin(value = "0", message = "학점은 0 이상이어야 합니다.")
        @WholeNumber(message = "학점은 정수로 입력해야 합니다.")
        @Schema(type = "integer", minimum = "0", example = "3")
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
