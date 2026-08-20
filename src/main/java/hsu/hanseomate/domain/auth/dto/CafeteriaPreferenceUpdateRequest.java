package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CafeteriaPreferenceUpdateRequest(
        @NotNull(message = "선호 식당은 필수입니다.")
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType preferredRestaurantType
) {
}
