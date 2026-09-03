package hsu.hanseomate.domain.popup.dto;

import hsu.hanseomate.domain.popup.entity.AppPopup;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ActiveAppPopupResponse(
        Long id,
        String title,
        String content,
        @Schema(nullable = true) String imageUrl,
        @Schema(format = "uri", nullable = true) String linkUrl,
        @Schema(nullable = true) LocalDateTime startsAt,
        @Schema(nullable = true) LocalDateTime endsAt,
        int displayOrder,
        @Schema(description = "오늘 하루 숨김 키에 함께 저장할 콘텐츠 버전")
        long revision
) {

    public static ActiveAppPopupResponse from(
            AppPopup popup,
            String currentImageUrl
    ) {
        return new ActiveAppPopupResponse(
                popup.getId(),
                popup.getTitle(),
                popup.getContent(),
                currentImageUrl,
                popup.getLinkUrl(),
                popup.getStartsAt(),
                popup.getEndsAt(),
                popup.getDisplayOrder(),
                popup.getRevision()
        );
    }
}
