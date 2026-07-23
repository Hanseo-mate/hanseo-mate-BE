package hsu.hanseomate.domain.club.dto;

import java.util.List;

public record ClubReviewStateResponse(
        Long clubId,
        long reviewerCount,
        List<ClubReviewOptionResponse> options
) {

    public ClubReviewStateResponse {
        options = List.copyOf(options);
    }
}
