package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SourceCellRequest(
        @Min(value = 1, message = "columnIndex는 1 이상이어야 합니다.") int columnIndex,
        @NotBlank @Size(max = 500) String headerName,
        @Pattern(
                regexp = "courseCode|courseName|sectionNo|gradeText|creditText|classHoursText|"
                        + "instructorName|scheduleText|classroomText|teamTeachingText|note|"
                        + "eligibilityText|areaText|classificationText|academicUnitText|"
                        + "departmentText|majorText|generalCategoryText|categoryLevelText|providerText",
                message = "canonicalField가 지원되는 표준 필드가 아닙니다."
        )
        String canonicalField,
        String value
) {
}
