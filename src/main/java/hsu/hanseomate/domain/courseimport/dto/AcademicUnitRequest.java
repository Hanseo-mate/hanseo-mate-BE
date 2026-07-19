package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AcademicUnitRequest(
        @NotBlank @Size(max = 255) String originalName,
        @NotBlank @Size(max = 255) String departmentName,
        @Size(max = 255) String majorName
) {
}
