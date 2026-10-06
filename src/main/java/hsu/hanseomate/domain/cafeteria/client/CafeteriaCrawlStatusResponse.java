package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 현재 프로세스의 최근 실행 상태 및 백그라운드 접수 응답.
 * starting/run_id는 접수 확인이며 저장 완료를 뜻하지 않는다.
 * 호환용 retry_count/max_retry_count/next_retry_at은 완료 판단에 사용하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaCrawlStatusResponse(
        @JsonProperty("run_id") String runId,
        @JsonProperty("status") String status,
        @JsonProperty("started_at") OffsetDateTime startedAt,
        @JsonProperty("finished_at") OffsetDateTime finishedAt,
        @JsonProperty("results") Map<RestaurantType, CafeteriaRestaurantCrawlResult> results
) {
}
