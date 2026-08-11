package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /cafeteria-crawl/run 요청 바디.
 */
public record CafeteriaCrawlRunRequest(
        @JsonProperty("url") String url,
        @JsonProperty("restaurant_type") String restaurantType,
        @JsonProperty("mode") String mode
) {

    public static CafeteriaCrawlRunRequest background(String url, String restaurantType) {
        return new CafeteriaCrawlRunRequest(url, restaurantType, "background");
    }
}
