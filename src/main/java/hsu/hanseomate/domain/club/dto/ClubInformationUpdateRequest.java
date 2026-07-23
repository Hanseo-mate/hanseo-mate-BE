package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import jakarta.validation.constraints.Size;

public record ClubInformationUpdateRequest(
        String introduction,

        String activityContent,

        @Size(max = 2048, message = "인스타그램 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String instagramUrl,

        @Size(max = 2048, message = "카카오톡 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String kakaoTalkUrl
) {
}
