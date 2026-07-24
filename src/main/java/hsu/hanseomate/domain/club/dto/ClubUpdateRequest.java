package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.global.validation.HttpUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClubUpdateRequest(
        @NotBlank(message = "동아리명은 필수입니다.")
        @Size(max = 100, message = "동아리명은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "한 줄 소개는 필수입니다.")
        @Size(max = 255, message = "한 줄 소개는 255자 이하여야 합니다.")
        String shortDescription,

        String introduction,

        String activityContent,

        @Size(max = 2048, message = "인스타그램 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String instagramUrl,

        @Size(max = 2048, message = "카카오톡 URL은 2048자 이하여야 합니다.")
        @HttpUrl
        String kakaoTalkUrl,

        String recruitmentContent
) {
}
