package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.util.List;

public record ClubDetailResponse(
        String backgroundImageUrl,
        String profileImageUrl,
        long likeCount,
        boolean likedByMe,
        String name,
        String shortDescription,
        List<ClubReviewOption> topReviewTags,
        String introduction,
        String activityContent,
        String instagramUrl,
        String kakaoTalkUrl,
        String recruitmentContent,
        long reviewerCount
) {

    public ClubDetailResponse {
        topReviewTags = List.copyOf(topReviewTags);
    }

    public static ClubDetailResponse from(
            Club club,
            long likeCount,
            boolean likedByMe,
            List<ClubReviewOption> topReviewTags,
            long reviewerCount
    ) {
        return new ClubDetailResponse(
                club.getBackgroundImageUrl(),
                club.getProfileImageUrl(),
                likeCount,
                likedByMe,
                club.getName(),
                club.getShortDescription(),
                topReviewTags,
                club.getIntroduction(),
                club.getActivityContent(),
                club.getInstagramUrl(),
                club.getKakaoTalkUrl(),
                club.getRecruitmentContent(),
                reviewerCount
        );
    }
}
