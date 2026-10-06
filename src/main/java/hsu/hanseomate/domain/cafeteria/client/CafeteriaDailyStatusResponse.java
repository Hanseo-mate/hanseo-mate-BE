package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 크롤러 DB의 영구 상태. 날짜는 KST 업무일, 시각은 UTC ISO 8601이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaDailyStatusResponse(
        @JsonProperty("business_date") LocalDate businessDate,
        @JsonProperty("pending_restaurant_types") List<RestaurantType> pendingRestaurantTypes,
        Map<RestaurantType, RestaurantDailyStatus> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RestaurantDailyStatus(
            @JsonProperty("completed_today") boolean completedToday,
            String status,
            @JsonProperty("business_date") LocalDate businessDate,
            @JsonProperty("run_id") String runId,
            @JsonProperty("last_attempt_at") OffsetDateTime lastAttemptAt,
            @JsonProperty("last_success_date") LocalDate lastSuccessDate,
            @JsonProperty("last_success_at") OffsetDateTime lastSuccessAt,
            String error
    ) {
    }
}
