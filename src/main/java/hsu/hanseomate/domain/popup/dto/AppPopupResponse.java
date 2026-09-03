package hsu.hanseomate.domain.popup.dto;

import hsu.hanseomate.domain.popup.entity.AppPopup;
import hsu.hanseomate.domain.popup.type.AppPopupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AppPopupResponse(
        Long id,
        String title,
        String content,
        @Schema(nullable = true) String imageUrl,
        @Schema(nullable = true) PopupNavigationResponse navigation,
        boolean enabled,
        AppPopupStatus status,
        @Schema(nullable = true) LocalDateTime startsAt,
        @Schema(nullable = true) LocalDateTime endsAt,
        int displayOrder,
        long revision,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AppPopupResponse from(
            AppPopup popup,
            LocalDateTime now,
            String currentImageUrl
    ) {
        return new AppPopupResponse(
                popup.getId(),
                popup.getTitle(),
                popup.getContent(),
                currentImageUrl,
                PopupNavigationResponse.from(popup.navigation()),
                popup.isEnabled(),
                popup.statusAt(now),
                popup.getStartsAt(),
                popup.getEndsAt(),
                popup.getDisplayOrder(),
                popup.getRevision(),
                popup.getCreatedAt(),
                popup.getUpdatedAt()
        );
    }
}
