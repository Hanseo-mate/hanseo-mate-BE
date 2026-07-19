package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import hsu.hanseomate.domain.courseimport.dto.type.IssueSeverity;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ParseIssueRequest(
        @NotNull IssueSeverity severity,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 2000) String message,
        String sheetName,
        @Min(value = 1, message = "rowNumber는 1 이상이어야 합니다.") Integer rowNumber,
        String field,
        String rawValue
) {
}
