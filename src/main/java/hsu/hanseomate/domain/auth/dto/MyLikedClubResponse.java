package hsu.hanseomate.domain.auth.dto;

import hsu.hanseomate.domain.club.entity.ClubLike;

public record MyLikedClubResponse(
        Long clubId,
        String clubName
) {

    public static MyLikedClubResponse from(ClubLike clubLike) {
        return new MyLikedClubResponse(
                clubLike.getClub().getId(),
                clubLike.getClub().getName()
        );
    }
}
