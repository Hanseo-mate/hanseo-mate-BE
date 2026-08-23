package hsu.hanseomate.domain.gradecalculator.dto;

import java.math.BigDecimal;
import java.util.List;

public record GradeOverviewResponse(
        BigDecimal maximumGpa,
        GradeSummaryResponse cumulativeSummary,
        List<GradeTermSummaryResponse> terms
) {
    public GradeOverviewResponse {
        terms = List.copyOf(terms);
    }
}
