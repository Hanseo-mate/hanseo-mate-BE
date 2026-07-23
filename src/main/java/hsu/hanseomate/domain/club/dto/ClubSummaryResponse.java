package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.util.List;

public record ClubSummaryResponse(
        Long id,
        String name,
        ClubCategory category,
        String profileImageUrl,
        String shortDescription,
        long likeCount,
        List<ClubReviewOption> topReviewTags
) {

    public ClubSummaryResponse {
        topReviewTags = List.copyOf(topReviewTags);
    }

    public static ClubSummaryResponse from(
            Club club,
            long likeCount,
            List<ClubReviewOption> topReviewTags
    ) {
        return new ClubSummaryResponse(
                club.getId(),
                club.getName(),
                club.getCategory(),
                club.getProfileImageUrl(),
                club.getShortDescription(),
                likeCount,
                topReviewTags
        );
    }
}
