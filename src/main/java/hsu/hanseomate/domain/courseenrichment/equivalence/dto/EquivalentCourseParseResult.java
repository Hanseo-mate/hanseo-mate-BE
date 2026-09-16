package hsu.hanseomate.domain.courseenrichment.equivalence.dto;

import hsu.hanseomate.domain.courseimport.dto.CourseImportIssueResponse;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import java.util.List;

public record EquivalentCourseParseResult(
        String schemaVersion,
        String parserVersion,
        String importId,
        String fileName,
        String rawFileSha256,
        String canonicalHash,
        int academicYear,
        int semester,
        List<EquivalentCourseGroupData> groups,
        List<CourseImportIssueResponse> issues
) {
    public EquivalentCourseParseResult {
        groups = List.copyOf(groups);
        issues = List.copyOf(issues);
    }

    public boolean requiresReview() {
        return issues.stream().anyMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }

    public int memberCount() {
        return groups.stream().mapToInt(group -> group.members().size()).sum();
    }
}
