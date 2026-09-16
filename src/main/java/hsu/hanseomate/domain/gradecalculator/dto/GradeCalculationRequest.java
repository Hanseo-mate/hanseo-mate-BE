package hsu.hanseomate.domain.gradecalculator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GradeCalculationRequest(
        @NotNull(message = "과목 목록은 필수입니다.")
        @Size(max = 100, message = "과목은 최대 100개까지 계산할 수 있습니다.")
        List<@NotNull(message = "과목 정보는 null일 수 없습니다.")
                @Valid GradeCalculationCourseRequest> courses
) {
}
