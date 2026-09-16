package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.gradecalculator.type.GradeCalculationStatus;
import java.math.BigDecimal;

public record GradeSummaryResponse(
        BigDecimal maximumGpa,
        BigDecimal totalCredits,
        BigDecimal gpaCredits,
        BigDecimal earnedCredits,
        BigDecimal averageGpa,
        int ungradedCourseCount,
        int unavailableCreditCourseCount,
        GradeCalculationStatus status
) {
}
