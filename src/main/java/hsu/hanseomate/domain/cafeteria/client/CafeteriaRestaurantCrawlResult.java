package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** /status의 results 아래 식당별 실행 결과. menus는 이 위치에서만 읽는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaRestaurantCrawlResult(
        String status,
        @JsonProperty("saved_daily_menus") Integer savedDailyMenus,
        Boolean updated,
        String error,
        List<CafeteriaDailyMenuCrawlDto> menus
) {
}
