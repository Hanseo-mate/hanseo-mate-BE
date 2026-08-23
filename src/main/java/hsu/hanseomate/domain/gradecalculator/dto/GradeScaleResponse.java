package hsu.hanseomate.domain.gradecalculator.dto;

import hsu.hanseomate.domain.gradecalculator.type.ExpectedGrade;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public record GradeScaleResponse(
        BigDecimal maximumGpa,
        List<GradeOptionResponse> grades
) {
    public static GradeScaleResponse hanseoUniversity() {
        return new GradeScaleResponse(
                new BigDecimal("4.5"),
                Arrays.stream(ExpectedGrade.values())
                        .map(GradeOptionResponse::from)
                        .toList()
        );
    }
}
