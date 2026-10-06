package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.util.List;

/** restaurant_types 생략 시 전체 식당 대상. only_pending=false는 수동 강제 수집이다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CafeteriaCrawlRunRequest(
        @JsonProperty("restaurant_types") List<RestaurantType> restaurantTypes,
        @JsonProperty("mode") String mode,
        @JsonProperty("only_pending") boolean onlyPending
) {
    public static CafeteriaCrawlRunRequest backgroundAllPending() {
        return background(null, true);
    }

    public static CafeteriaCrawlRunRequest backgroundAll() {
        return background(null, false);
    }

    public static CafeteriaCrawlRunRequest background(
            List<RestaurantType> restaurantTypes, boolean onlyPending
    ) {
        return new CafeteriaCrawlRunRequest(restaurantTypes, "background", onlyPending);
    }
}
