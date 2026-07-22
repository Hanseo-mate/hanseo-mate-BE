package hsu.hanseomate.domain.courseimport.dto;

import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import java.util.List;

public record CourseImportResponse(
        String importId,
        StorageStatus storageStatus,
        boolean databaseChanged,
        int offeringCount,
        String message,
        List<CourseImportIssueResponse> reviewIssues
) {
}
