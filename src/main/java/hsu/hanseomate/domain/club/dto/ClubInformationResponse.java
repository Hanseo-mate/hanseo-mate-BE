package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;

public record ClubInformationResponse(
        String introduction,
        String activityContent,
        String instagramUrl,
        String kakaoTalkUrl
) {

    public static ClubInformationResponse from(Club club) {
        return new ClubInformationResponse(
                club.getIntroduction(),
                club.getActivityContent(),
                club.getInstagramUrl(),
                club.getKakaoTalkUrl()
        );
    }
}
