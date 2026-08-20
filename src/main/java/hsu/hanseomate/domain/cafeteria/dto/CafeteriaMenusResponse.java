package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CafeteriaMenusResponse(
        @Schema(
                nullable = true,
                allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"}
        )
        RestaurantType preferredRestaurantType,
        List<CafeteriaRestaurantMenusResponse> restaurants
) {

    public CafeteriaMenusResponse {
        restaurants = List.copyOf(restaurants);
    }
}
