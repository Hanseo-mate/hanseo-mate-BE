package hsu.hanseomate.domain.courseenrichment.crossmajor.dto;

import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;

public record CrossMajorRecognitionIssueResponse(
        IssueSeverity severity,
        String code,
        String message,
        String sheetName,
        Integer rowNumber,
        String field,
        String rawValue
) {
    public static CrossMajorRecognitionIssueResponse error(
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CrossMajorRecognitionIssueResponse(
                IssueSeverity.ERROR, code, message, sheetName, rowNumber, field, rawValue
        );
    }

    public static CrossMajorRecognitionIssueResponse warning(
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CrossMajorRecognitionIssueResponse(
                IssueSeverity.WARNING, code, message, sheetName, rowNumber, field, rawValue
        );
    }
}
