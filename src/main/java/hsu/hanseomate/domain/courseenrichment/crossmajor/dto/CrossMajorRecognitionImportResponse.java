package hsu.hanseomate.domain.courseenrichment.crossmajor.dto;

import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import java.util.List;
import java.util.UUID;

public record CrossMajorRecognitionImportResponse(
        UUID importId,
        StorageStatus storageStatus,
        boolean databaseChanged,
        int policyYear,
        int uploadedSemester,
        int ruleCount,
        String message,
        List<CrossMajorRecognitionIssueResponse> reviewIssues
) {
    public CrossMajorRecognitionImportResponse {
        reviewIssues = List.copyOf(reviewIssues);
    }
}
