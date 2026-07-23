package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;

public record ClubRecruitmentResponse(
        String recruitmentContent
) {

    public static ClubRecruitmentResponse from(Club club) {
        return new ClubRecruitmentResponse(club.getRecruitmentContent());
    }
}
