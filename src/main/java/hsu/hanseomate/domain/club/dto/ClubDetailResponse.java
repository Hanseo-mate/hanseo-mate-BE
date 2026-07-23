package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.util.List;

public record ClubDetailResponse(
        Long id,
        String name,
        String profileImageUrl,
        String backgroundImageUrl,
        String shortDescription,
        long likeCount,
        List<ClubReviewOption> topReviewTags
) {

    public ClubDetailResponse {
        topReviewTags = List.copyOf(topReviewTags);
    }

    public static ClubDetailResponse from(
            Club club,
            long likeCount,
            List<ClubReviewOption> topReviewTags
    ) {
        return new ClubDetailResponse(
                club.getId(),
                club.getName(),
                club.getProfileImageUrl(),
                club.getBackgroundImageUrl(),
                club.getShortDescription(),
                likeCount,
                topReviewTags
        );
    }
}
