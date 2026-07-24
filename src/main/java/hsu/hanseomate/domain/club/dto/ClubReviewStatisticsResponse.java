package hsu.hanseomate.domain.club.dto;

import java.util.List;

public record ClubReviewStatisticsResponse(
        List<ClubReviewOptionResponse> options
) {

    public ClubReviewStatisticsResponse {
        options = List.copyOf(options);
    }
}
