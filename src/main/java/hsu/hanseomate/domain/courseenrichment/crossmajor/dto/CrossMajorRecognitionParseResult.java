package hsu.hanseomate.domain.courseenrichment.crossmajor.dto;

import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.List;

public record CrossMajorRecognitionParseResult(
        String fileName,
        String rawFileSha256,
        String canonicalDataSha256,
        int policyYear,
        int uploadedSemester,
        String sourceSheet,
        int rawRowCount,
        List<CrossMajorRecognitionRuleData> rules,
        List<CrossMajorRecognitionIssueResponse> issues
) {
    public CrossMajorRecognitionParseResult {
        rules = List.copyOf(rules);
        issues = List.copyOf(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    public int warningCount() {
        return (int) issues.stream()
                .filter(issue -> issue.severity() == IssueSeverity.WARNING)
                .count();
    }
}
