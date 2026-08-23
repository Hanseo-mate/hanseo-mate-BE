package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.gradecalculator.type.GradeCalculationStatus;
import java.math.BigDecimal;

public record GradeCalculationResponse(
        BigDecimal maximumGpa,
        BigDecimal appliedCredits,
        BigDecimal gpaCredits,
        BigDecimal earnedCredits,
        BigDecimal expectedGpa,
        int ungradedCourseCount,
        GradeCalculationStatus status
) {
}
