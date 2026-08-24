package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.util.Comparator;
import java.util.List;

public record MyClubReviewResponse(
        Long clubId,
        String clubName,
        String profileImageUrl,
        List<ClubReviewOption> reviewTags
) {

    public MyClubReviewResponse {
        reviewTags = List.copyOf(reviewTags);
    }

    public static MyClubReviewResponse from(ClubReview review) {
        List<ClubReviewOption> sortedReviewTags = review.getReviewTags().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .toList();
        return new MyClubReviewResponse(
                review.getClub().getId(),
                review.getClub().getName(),
                review.getClub().getProfileImageUrl(),
                sortedReviewTags
        );
    }
}
