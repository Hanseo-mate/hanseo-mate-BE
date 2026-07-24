package hsu.hanseomate.domain.club.dto;

import java.util.UUID;

public record ClubImageUploadResponse(
        UUID imageId,
        String imageUrl
) {
}
