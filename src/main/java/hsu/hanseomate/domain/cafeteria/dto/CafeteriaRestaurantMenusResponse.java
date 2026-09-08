package hsu.hanseomate.domain.cafeteria.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CafeteriaRestaurantMenusResponse(
        @Schema(allowableValues = {"SEOSAN", "TAEAN"})
        CampusCode campusCode,
        @Schema(allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType restaurantType,
        List<DailyMenuDTO> dailyMenus
) {

    public CafeteriaRestaurantMenusResponse {
        dailyMenus = List.copyOf(dailyMenus);
    }
}
