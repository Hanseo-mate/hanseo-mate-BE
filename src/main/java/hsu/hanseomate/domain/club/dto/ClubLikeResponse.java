package hsu.hanseomate.domain.club.dto;

public record ClubLikeResponse(
        Long clubId,
        boolean likedByMe,
        long likeCount
) {
}
