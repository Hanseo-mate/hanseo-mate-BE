package hsu.hanseomate.domain.club.repository;

import hsu.hanseomate.domain.club.type.ClubReviewOption;

public interface ClubReviewCountProjection {

    Long getClubId();

    ClubReviewOption getReviewTag();

    long getReviewCount();
}
