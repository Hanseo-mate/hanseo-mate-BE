package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /cafeteria-crawl/status 응답 바디.
 */
public record CafeteriaCrawlStatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("last_run_at") String lastRunAt,
        @JsonProperty("message") String message
) {
}
