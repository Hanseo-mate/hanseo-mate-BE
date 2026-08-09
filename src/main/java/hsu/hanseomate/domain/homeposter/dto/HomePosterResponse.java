package hsu.hanseomate.domain.homeposter.dto;

import hsu.hanseomate.domain.homeposter.entity.HomePoster;
import java.time.LocalDateTime;

public record HomePosterResponse(
        Long id,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static HomePosterResponse from(HomePoster poster) {
        return new HomePosterResponse(
                poster.getId(),
                poster.getImageUrl(),
                poster.getCreatedAt(),
                poster.getUpdatedAt()
        );
    }
}
