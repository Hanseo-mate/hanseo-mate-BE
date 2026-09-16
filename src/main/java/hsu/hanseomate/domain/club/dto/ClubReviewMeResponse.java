package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.util.Comparator;
import java.util.List;

public record ClubReviewMeResponse(
        boolean hasReview,
        List<ClubReviewOption> reviewTags
) {

    public ClubReviewMeResponse {
        reviewTags = List.copyOf(reviewTags);
    }

    public static ClubReviewMeResponse empty() {
        return new ClubReviewMeResponse(false, List.of());
    }

    public static ClubReviewMeResponse from(ClubReview review) {
        List<ClubReviewOption> sortedReviewTags = review.getReviewTags().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
        return new ClubReviewMeResponse(true, sortedReviewTags);
    }
}
