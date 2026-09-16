package hsu.hanseomate.domain.cafeteria.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * GET /health 응답 바디.
 */
public record CafeteriaHealthResponse(
        @JsonProperty("status") String status
) {
}
