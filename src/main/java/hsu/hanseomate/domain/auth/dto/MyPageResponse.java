package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.club.entity.ClubLike;
import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.type.UserRole;
import java.time.LocalDateTime;
import java.util.List;

public record MyPageResponse(
        Long userId,
        String loginId,
        UserRole role,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MyClubReviewResponse> clubReviews,
        List<MyLikedClubResponse> likedClubs
) {

    public MyPageResponse {
        clubReviews = List.copyOf(clubReviews);
        likedClubs = List.copyOf(likedClubs);
    }

    public static MyPageResponse from(
            UserAccount userAccount,
            List<ClubReview> clubReviews,
            List<ClubLike> clubLikes
    ) {
        return new MyPageResponse(
                userAccount.getId(),
                userAccount.getLoginId(),
                userAccount.getRole(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt(),
                clubReviews.stream().map(MyClubReviewResponse::from).toList(),
                clubLikes.stream().map(MyLikedClubResponse::from).toList()
        );
    }
}
