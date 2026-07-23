package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ClassroomRequest(
        @Size(max = 100) String campusCode,
        @Size(max = 255) String buildingName,
        @Size(max = 100) String roomNumber,
        @NotBlank @Size(max = 500) String originalValue
) {
}
