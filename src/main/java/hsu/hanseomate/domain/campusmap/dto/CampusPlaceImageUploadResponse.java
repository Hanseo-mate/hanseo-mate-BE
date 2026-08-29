package hsu.hanseomate.domain.campusmap.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CampusPlaceImageUploadResponse(
        @Schema(format = "uri")
        String imageUrl
) {
}
