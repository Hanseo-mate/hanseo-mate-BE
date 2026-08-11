package hsu.hanseomate.domain.auth.dto;

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
        List<MyClubReviewResponse> clubReviews
) {

    public MyPageResponse {
        clubReviews = List.copyOf(clubReviews);
    }

    public static MyPageResponse from(
            UserAccount userAccount,
            List<ClubReview> clubReviews
    ) {
        return new MyPageResponse(
                userAccount.getId(),
                userAccount.getLoginId(),
                userAccount.getRole(),
                userAccount.getCreatedAt(),
                userAccount.getUpdatedAt(),
                clubReviews.stream().map(MyClubReviewResponse::from).toList()
        );
    }
}
