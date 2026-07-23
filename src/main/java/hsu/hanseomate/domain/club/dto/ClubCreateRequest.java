package hsu.hanseomate.domain.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClubCreateRequest(
        @NotBlank(message = "동아리명은 필수입니다.")
        @Size(max = 100, message = "동아리명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "분과는 필수입니다.")
        @Size(max = 30, message = "분과는 30자 이하여야 합니다.")
        String category
) {
}
