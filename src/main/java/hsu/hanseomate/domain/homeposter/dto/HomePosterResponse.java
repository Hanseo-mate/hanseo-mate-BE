package hsu.hanseomate.domain.homeposter.dto;

import hsu.hanseomate.domain.homeposter.entity.HomePoster;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record HomePosterResponse(
        Long id,
        String imageUrl,
        @Schema(
                description = "포스터 클릭 시 이동할 선택 링크",
                format = "uri",
                nullable = true,
                maxLength = 2048
        )
        String linkUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static HomePosterResponse from(HomePoster poster) {
        return new HomePosterResponse(
                poster.getId(),
                poster.getImageUrl(),
                poster.getLinkUrl(),
                poster.getCreatedAt(),
                poster.getUpdatedAt()
        );
    }
}
