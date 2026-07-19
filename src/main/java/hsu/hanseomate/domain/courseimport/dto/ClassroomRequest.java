package hsu.hanseomate.domain.courseimport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ClassroomRequest(
        String campusCode,
        String buildingName,
        String roomNumber,
        @NotBlank @Size(max = 500) String originalValue
) {
}
