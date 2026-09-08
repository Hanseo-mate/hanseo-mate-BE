package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CampusPreferenceUpdateRequest(
        @NotNull(message = "선호 캠퍼스는 필수입니다.")
        @Schema(allowableValues = {"SEOSAN", "TAEAN"})
        CampusCode preferredCampusCode
) {
}
