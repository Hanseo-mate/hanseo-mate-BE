package hsu.hanseomate.domain.gradecalculator.dto;

public record GradeCompactSummaryResponse(
        GradeSummaryResponse termSummary,
        GradeSummaryResponse cumulativeSummary
) {
}
