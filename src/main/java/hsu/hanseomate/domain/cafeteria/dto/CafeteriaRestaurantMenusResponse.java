package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CafeteriaRestaurantMenusResponse(
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType restaurantType,
        List<DailyMenuDTO> dailyMenus
) {

    public CafeteriaRestaurantMenusResponse {
        dailyMenus = List.copyOf(dailyMenus);
    }
}
