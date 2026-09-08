package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CafeteriaMenusResponse(
        @Schema(
                nullable = true,
                allowableValues = {"SEOSAN", "TAEAN"}
        )
        CampusCode preferredCampusCode,
        List<CafeteriaRestaurantMenusResponse> restaurants
) {

    public CafeteriaMenusResponse {
        restaurants = List.copyOf(restaurants);
    }
}
