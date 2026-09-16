package hsu.hanseomate.domain.courseenrichment.equivalence.dto;

import hsu.hanseomate.domain.courseimport.dto.CourseImportIssueResponse;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import java.util.List;

public record EquivalentCourseImportResponse(
        String importId,
        StorageStatus storageStatus,
        boolean databaseChanged,
        int groupCount,
        int memberCount,
        String message,
        List<CourseImportIssueResponse> issues
) {
    public EquivalentCourseImportResponse {
        issues = List.copyOf(issues);
    }
}
