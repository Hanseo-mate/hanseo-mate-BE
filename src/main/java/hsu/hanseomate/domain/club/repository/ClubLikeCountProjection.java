package hsu.hanseomate.domain.club.repository;

public interface ClubLikeCountProjection {

    Long getClubId();

    long getLikeCount();
}
