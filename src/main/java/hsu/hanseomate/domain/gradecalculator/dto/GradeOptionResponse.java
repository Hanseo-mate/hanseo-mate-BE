package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import java.math.BigDecimal;

public record GradeOptionResponse(
        ExpectedGrade grade,
        BigDecimal gradePoint,
        boolean includedInGpa,
        boolean creditEarned
) {
    public static GradeOptionResponse from(ExpectedGrade grade) {
        return new GradeOptionResponse(
                grade,
                grade.getGradePoint(),
                grade.isIncludedInGpa(),
                grade.isCreditEarned()
        );
    }
}
