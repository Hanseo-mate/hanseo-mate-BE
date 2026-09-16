package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.homeposter.dto.HomePosterResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record HomePosterItemResponse(
        Long id,
        String imageUrl,
        @Schema(
                description = "포스터 클릭 시 이동할 선택 링크",
                format = "uri",
                nullable = true,
                maxLength = 2048
        )
        String linkUrl
) {

    public static HomePosterItemResponse from(HomePosterResponse poster) {
        return new HomePosterItemResponse(
                poster.id(),
                poster.imageUrl(),
                poster.linkUrl()
        );
    }
}
