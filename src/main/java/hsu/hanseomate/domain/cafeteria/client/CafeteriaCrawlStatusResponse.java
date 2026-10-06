package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /cafeteria-crawl/status 응답 바디.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CafeteriaCrawlStatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("last_run_at") String lastRunAt,
        @JsonProperty("message") String message
) {
}
