package hsu.hanseomate.domain.courseimport.dto;

import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;

public record CourseImportIssueResponse(
        IssueSeverity severity,
        String code,
        String message,
        String sheetName,
        Integer rowNumber,
        String field,
        String rawValue
) {
    public CourseImportIssueResponse {
        code = truncate(code, 100);
        message = truncate(message, 2000);
        sheetName = truncate(sheetName, 255);
        field = truncate(field, 100);
        rawValue = truncate(rawValue, 2000);
    }

    public static CourseImportIssueResponse from(ParseIssueRequest issue) {
        return new CourseImportIssueResponse(
                issue.severity(),
                issue.code(),
                issue.message(),
                issue.sheetName(),
                issue.rowNumber(),
                issue.field(),
                issue.rawValue()
        );
    }

    public static CourseImportIssueResponse error(
            String code,
            String message,
            String sheetName,
            Integer rowNumber,
            String field,
            String rawValue
    ) {
        return new CourseImportIssueResponse(
                IssueSeverity.ERROR,
                code,
                message,
                sheetName,
                rowNumber,
                field,
                rawValue
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
